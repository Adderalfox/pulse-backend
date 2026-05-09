package services

import models.{AwardRecommendation, RankedAward, RecommendationResult}
import repositories.{AwardRecommendationRepository, AppreciationRepository}
import utils.{EmbeddingTaskType, GeminiClient, QdrantClientWrapper}
import play.api.Logging
import play.api.libs.json._

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

// ---------------------------------------------------------------------------
// Internal intermediate types for the agentic loop
// ---------------------------------------------------------------------------

private case class Pass1Result(
                                dominantSignals: List[String],
                                absentOrUnclear: List[String],
                                partialSummary: String
                              )

@Singleton
class AwardRecommendationService @Inject()(
                                            geminiClient: GeminiClient,
                                            qdrantClient: QdrantClientWrapper,
                                            awardRecommendationRepo: AwardRecommendationRepository,
                                            appreciationRepo: AppreciationRepository
                                          )(implicit ec: ExecutionContext) extends Logging {

  private val MinAppreciationThreshold = 5
  private val ScoreThreshold = 0.55f
  private val TopN = 3

  // ---------------------------------------------------------------------------
  // Public entry point
  // ---------------------------------------------------------------------------

  def recommendAwards(
                       nomineeId: String,
                       requestedBy: String,
                       companyId: String,
                       departmentId: String
                     ): Future[Either[String, RecommendationResult]] = {

    // Guard: minimum appreciation count
    appreciationRepo.countForUser(nomineeId).flatMap {
      case count if count < MinAppreciationThreshold =>
        Future.successful(Left(
          s"Insufficient data: employee has only $count appreciation(s). " +
            s"At least $MinAppreciationThreshold are required for a reliable recommendation."
        ))

      case _ =>
        buildAgenticProfile(nomineeId).flatMap {
          case Left(err) => Future.successful(Left(err))
          case Right(profile) =>
            matchAwards(profile, companyId, departmentId).flatMap {
              case Left(err) => Future.successful(Left(err))
              case Right(Nil) =>
                Future.successful(Left(
                  "No awards matched the employee's profile above the required score threshold (0.65). " +
                    "Consider adding more award definitions or ensuring the employee has richer appreciation history."
                ))
              case Right(ranked) =>
                val result = RecommendationResult(
                  nomineeId = nomineeId,
                  profileSummary = profile,
                  rankedAwards = ranked
                )
                cacheResult(result, requestedBy).map(_ => Right(result))
            }
        }
    }
  }

  def getRecommendationHistory(nomineeId: String): Future[Seq[AwardRecommendation]] =
    awardRecommendationRepo.findByNominee(nomineeId)

  // ---------------------------------------------------------------------------
  // Step 1 — Agentic profile building (max 2 passes)
  // ---------------------------------------------------------------------------

  private def buildAgenticProfile(nomineeId: String): Future[Either[String, String]] = {

    val pass1Query = "professional skills contributions team impact performance"

    geminiClient.embedText(pass1Query, EmbeddingTaskType.RETRIEVAL_QUERY).flatMap {
      case Left(err) => Future.successful(Left(s"Pass 1 embedding failed: $err"))
      case Right(vec) =>
        qdrantClient.searchForNominee(
          queryVector = vec,
          nomineeId = nomineeId,
          limit = 15,
          scoreThreshold = 0.0f
        ).flatMap { points =>

          val messages = extractMessages(points)

          if (messages.isEmpty)
            return Future.successful(Left(s"No appreciations found in Qdrant for nominee $nomineeId"))

          runPass1(messages).flatMap {
            case Left(err) => Future.successful(Left(err))

            case Right(pass1) if pass1.absentOrUnclear.isEmpty =>
              // Agent decision: profile is complete — skip pass 2
              logger.info(s"[AwardRecommendationService] Pass 2 skipped for $nomineeId — no absent signals.")
              Future.successful(Right(pass1.partialSummary))

            case Right(pass1) =>
              // Agent decision: absent signals detected — run targeted pass 2
              logger.info(
                s"[AwardRecommendationService] Pass 2 triggered for $nomineeId. " +
                  s"Absent signals: ${pass1.absentOrUnclear.mkString(", ")}"
              )
              runPass2(nomineeId, pass1).map {
                case Left(err) => Left(err)
                case Right(supplementarySummary) =>
                  val merged = mergeProfile(pass1.partialSummary, supplementarySummary)
                  Right(merged)
              }
          }
        }
    }
  }

  // ---------------------------------------------------------------------------
  // Pass 1 — broad retrieval + dominant signal extraction
  // ---------------------------------------------------------------------------

  private def runPass1(messages: List[String]): Future[Either[String, Pass1Result]] = {
    val truncatedMessages = messages.take(5).map(_.take(150))
    val messagesText = truncatedMessages.zipWithIndex
      .map { case (m, i) => s"${i + 1}. \"$m\"" }.mkString("\n")

    // --- Call A: extract signals only (arrays — model handles these fine) ---
    val signalsSystemPrompt = "You are an HR analyst. Extract skills from peer recognition messages. Return only JSON."
    val signalsUserPrompt =
      s"""Peer recognition messages:
         |$messagesText
         |
         |Return ONLY this JSON, no other text:
         |{"dominantSignals":["skill1","skill2"],"absentOrUnclear":["gap1","gap2"]}""".stripMargin

    geminiClient.generateContent(signalsSystemPrompt, signalsUserPrompt, temperature = 0.1).flatMap {
      case Left(err) => Future.successful(Left(s"Pass 1 signals failed: $err"))
      case Right(raw) =>
        parseSignals(raw) match {
          case Left(err) => Future.successful(Left(err))
          case Right((dominant, absent)) =>

            // --- Call B: generate summary only (single string — small output) ---
            val summarySystemPrompt = "You are an HR analyst. Write a brief factual employee summary. Return only JSON."
            val summaryUserPrompt =
              s"""Based on these skills: ${dominant.mkString(", ")}
                 |
                 |Return ONLY this JSON, no other text:
                 |{"s":"One to two sentence factual summary of the employee."}""".stripMargin

            geminiClient.generateContent(summarySystemPrompt, summaryUserPrompt, temperature = 0.1).map {
              case Left(err) => Left(s"Pass 1 summary failed: $err")
              case Right(raw2) =>
                parseSummary(raw2) match {
                  case Left(err) => Left(err)
                  case Right(summary) => Right(Pass1Result(dominant, absent, summary))
                }
            }
        }
    }
  }

  private def parseSignals(raw: String): Either[String, (List[String], List[String])] =
    try {
      val json = Json.parse(raw)
      val dominant = (json \ "dominantSignals").asOpt[List[String]].getOrElse(Nil)
      val absent   = (json \ "absentOrUnclear").asOpt[List[String]].getOrElse(Nil)
      if (dominant.isEmpty) Left("dominantSignals was empty")
      else Right((dominant, absent))
    } catch {
      case e: Exception => Left(e.getMessage)
    }

  private def parseSummary(raw: String): Either[String, String] =
    try {
      val json = Json.parse(raw)
      // try short key "s" first, then verbose fallbacks
      val summary = (json \ "s").asOpt[String]
        .orElse((json \ "summary").asOpt[String])
        .orElse((json \ "partialSummary").asOpt[String])
        .getOrElse("")
      if (summary.isEmpty) Left("summary was empty")
      else Right(summary)
    } catch {
      case e: Exception => Left(e.getMessage)
    }

//  private def parsePass1Response(raw: String): Either[String, Pass1Result] =
//    try {
//      val json = Json.parse(raw)
//      val dominant = (json \ "dominantSignals").asOpt[List[String]].getOrElse(Nil)
//      val absent   = (json \ "absentOrUnclear").asOpt[List[String]].getOrElse(Nil)
//      // fallback key variants the model sometimes emits
//      val summary  = (json \ "partialSummary")
//        .asOpt[String]
//        .orElse((json \ "partial_summary").asOpt[String])
//        .orElse((json \ "summary").asOpt[String])
//        .getOrElse("")
//      if (summary.isEmpty) Left("partialSummary was empty")
//      else Right(Pass1Result(dominant, absent, summary))
//    } catch {
//      case e: Exception => Left(e.getMessage)
//    }

  // ---------------------------------------------------------------------------
  // Pass 2 — targeted retrieval for absent signals
  // ---------------------------------------------------------------------------

  // Pass 1 — unchanged from last version, already correct

  // Pass 2 — updated to short-key pattern
  private def runPass2(nomineeId: String, pass1: Pass1Result): Future[Either[String, String]] = {
    val targetedQuery = pass1.absentOrUnclear.mkString(" ")

    geminiClient.embedText(targetedQuery, EmbeddingTaskType.RETRIEVAL_QUERY).flatMap {
      case Left(err) => Future.successful(Left(s"Pass 2 embedding failed: $err"))
      case Right(vec) =>
        qdrantClient.searchForNominee(
          queryVector = vec,
          nomineeId = nomineeId,
          limit = 10,
          scoreThreshold = 0.0f
        ).flatMap { points =>

          val messages = extractMessages(points).take(5).map(_.take(150)) // match Pass 1 caps

          if (messages.isEmpty) {
            logger.info(s"[AwardRecommendationService] Pass 2 found no additional evidence for $nomineeId")
            return Future.successful(Right(""))
          }

          val absentList = pass1.absentOrUnclear.mkString(", ")
          val messagesText = messages.zipWithIndex
            .map { case (m, i) => s"${i + 1}. \"$m\"" }.mkString("\n")

          val systemPrompt = "You are an HR analyst. Analyse peer messages for evidence of specific skills. Return only JSON."
          val userPrompt =
            s"""Messages:
               |$messagesText
               |
               |Do these show evidence of: $absentList?
               |
               |Return ONLY this JSON, no other text:
               |{"s":"1-2 sentence answer on what evidence was or was not found."}""".stripMargin

          geminiClient.generateContent(systemPrompt, userPrompt, temperature = 0.1).map {
            case Left(err) => Left(s"Pass 2 generation failed: $err")
            case Right(raw) =>
              try {
                val json = Json.parse(raw)
                Right(
                  (json \ "s").asOpt[String]
                    .orElse((json \ "supplementary_summary").asOpt[String])
                    .getOrElse("")
                )
              } catch {
                case _: Exception => Right("") // non-fatal
              }
          }
        }
    }
  }

  // ---------------------------------------------------------------------------
  // Profile merge
  // ---------------------------------------------------------------------------

  private def mergeProfile(partial: String, supplementary: String): String =
    if (supplementary.isEmpty) partial
    else s"$partial $supplementary".trim

  // ---------------------------------------------------------------------------
  // Step 2 — Award matching
  // ---------------------------------------------------------------------------

  private def matchAwards(
                           profile: String,
                           companyId: String,
                           departmentId: String
                         ): Future[Either[String, List[RankedAward]]] =

    geminiClient.embedText(profile, EmbeddingTaskType.RETRIEVAL_QUERY).flatMap {
      case Left(err) => Future.successful(Left(s"Profile embedding failed: $err"))
      case Right(profileVector) =>
        qdrantClient.searchAwardDefinitions(
          profileVector = profileVector,
          companyId = companyId,
          departmentId = departmentId,
          limit = TopN * 3 // over-fetch then threshold-filter
        ).map { scoredPoints =>
          val ranked = scoredPoints
            .filter(_.getScore >= ScoreThreshold)
            .sortBy(-_.getScore)
            .take(TopN)
            .flatMap { sp =>
              val payload = sp.getPayload
              for {
                awardId <- Option(payload.get("award_id")).map(_.getStringValue)
                awardName <- Option(payload.get("award_name")).map(_.getStringValue)
                criteriaText <- Option(payload.get("criteria_text")).map(_.getStringValue)
              } yield RankedAward(
                awardId = awardId,
                awardName = awardName,
                score = sp.getScore,
                criteriaText = criteriaText
              )
            }
            .toList
          Right(ranked)
        }.recover { case e =>
          Left(s"Award search failed: ${e.getMessage}")
        }
    }

  // ---------------------------------------------------------------------------
  // Step 3 — Cache result
  // ---------------------------------------------------------------------------

  private def cacheResult(result: RecommendationResult, requestedBy: String): Future[Unit] = {
    val rankedJson = Json.toJson(result.rankedAwards).toString()
    val rec = AwardRecommendation(
      id = UUID.randomUUID().toString,
      nomineeId = result.nomineeId,
      requestedBy = requestedBy,
      recommendedAwards = rankedJson,
      profileSummary = result.profileSummary
    )
    awardRecommendationRepo.insert(rec).map(_ => ()).recover { case e =>
      logger.error(s"[AwardRecommendationService] Failed to cache recommendation: ${e.getMessage}")
    }
  }

  // ---------------------------------------------------------------------------
  // Utility
  // ---------------------------------------------------------------------------

  private def extractMessages(points: List[io.qdrant.client.grpc.Points.ScoredPoint]): List[String] =
    points.flatMap { p =>
      Option(p.getPayload.get("message_preview")).map(_.getStringValue).filter(_.nonEmpty)
    }
}