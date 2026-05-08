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
    val systemPrompt =
      """You are an HR intelligence engine. Your job is to analyse peer recognition messages
        |and extract a factual profile of the employee's demonstrated skills and behaviours.
        |Be concise and evidence-based. Do not invent signals not present in the text.""".stripMargin

    val userPrompt =
      s"""Below are peer recognition messages received by an employee.
         |Summarize their dominant skills and behavioural patterns.
         |
         |Messages:
         |${messages.zipWithIndex.map { case (m, i) => s"${i + 1}. \"$m\"" }.mkString("\n")}
         |
         |Return ONLY a JSON object with exactly these three fields:
         |{
         |  "dominantSignals": ["list of clear signals found in the evidence"],
         |  "absentOrUnclear": ["signals that are missing or too vague to confirm"],
         |  "partialSummary": "2-3 sentence factual summary of the employee's profile"
         |}
         |No markdown, no preamble, no explanation — pure JSON only.""".stripMargin

    geminiClient.generateContent(systemPrompt, userPrompt, temperature = 0.1).map {
      case Left(err) => Left(s"Pass 1 generation failed: $err")
      case Right(raw) =>
        parsePass1Response(raw) match {
          case Left(parseErr) =>
            logger.warn(s"[AwardRecommendationService] Pass 1 parse error: $parseErr. Raw: ${raw.take(300)}")
            Left(s"Failed to parse profile analysis: $parseErr")
          case Right(result) => Right(result)
        }
    }
  }

  private def parsePass1Response(raw: String): Either[String, Pass1Result] =
    try {
      val json = Json.parse(raw)
      val dominant = (json \ "dominantSignals").asOpt[List[String]].getOrElse(Nil)
      val absent = (json \ "absentOrUnclear").asOpt[List[String]].getOrElse(Nil)
      val summary = (json \ "partialSummary").asOpt[String].getOrElse("")
      if (summary.isEmpty) Left("partialSummary was empty")
      else Right(Pass1Result(dominant, absent, summary))
    } catch {
      case e: Exception => Left(e.getMessage)
    }

  // ---------------------------------------------------------------------------
  // Pass 2 — targeted retrieval for absent signals
  // ---------------------------------------------------------------------------

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

          val messages = extractMessages(points)

          if (messages.isEmpty) {
            // No additional evidence found — return empty supplement gracefully
            logger.info(s"[AwardRecommendationService] Pass 2 found no additional evidence for $nomineeId")
            return Future.successful(Right(""))
          }

          val absentList = pass1.absentOrUnclear.mkString(", ")

          val systemPrompt =
            """You are an HR intelligence engine. Analyse the provided peer recognition messages
              |and determine whether they contain evidence of specific signals.
              |Be factual and concise. Do not invent evidence.""".stripMargin

          val userPrompt =
            s"""These are additional peer recognition messages for the same employee.
               |Does this evidence show signals of: $absentList?
               |
               |Messages:
               |${messages.zipWithIndex.map { case (m, i) => s"${i + 1}. \"$m\"" }.mkString("\n")}
               |
               |Return ONLY a JSON object:
               |{
               |  "supplementary_summary": "1-2 sentences on what additional evidence was or was not found"
               |}
               |No markdown, no preamble — pure JSON only.""".stripMargin

          geminiClient.generateContent(systemPrompt, userPrompt, temperature = 0.1).map {
            case Left(err) => Left(s"Pass 2 generation failed: $err")
            case Right(raw) =>
              try {
                val json = Json.parse(raw)
                Right((json \ "supplementary_summary").asOpt[String].getOrElse(""))
              } catch {
                case _: Exception => Right("") // non-fatal — degrade gracefully
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