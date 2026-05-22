package services

import models.{AwardRecommendation, RankedAward, RecommendationResult}
import repositories.{AwardRecommendationRepository, AppreciationRepository}
import utils.{EmbeddingTaskType, GeminiClient, QdrantClientWrapper}
import play.api.Logging
import play.api.libs.json._

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

private case class MatchedAward(
                                 awardId:        String,
                                 awardName:      String,
                                 criteriaText:   String,
                                 criteriaVector: Seq[Float],   // stored in Qdrant — no re-embed needed
                                 score:          Float,        // cosine similarity: employeeProfile ↔ awardCriteria
                                 evidence:       List[String]  // top-3 appreciation messages most relevant to THIS award
                               )

@Singleton
class AwardRecommendationService @Inject()(
                                            geminiClient:            GeminiClient,
                                            qdrantClient:            QdrantClientWrapper,
                                            awardRecommendationRepo: AwardRecommendationRepository,
                                            appreciationRepo:        AppreciationRepository
                                          )(implicit ec: ExecutionContext) extends Logging {

  private val MinAppreciationThreshold = 5
  private val ScoreThreshold           = 0.55f
  private val TopN                     = 3
  private val EvidencePerAward         = 3   // appreciation messages per award sent to LLM

  def recommendAwards(
                       nomineeId:    String,
                       requestedBy:  String,
                       companyId:    String,
                       departmentId: String
                     ): Future[Either[String, RecommendationResult]] = {

    appreciationRepo.countForUser(nomineeId).flatMap {

      case count if count < MinAppreciationThreshold =>
        Future.successful(Left(
          s"Insufficient data: employee has only $count appreciation(s). " +
            s"At least $MinAppreciationThreshold are required for a reliable recommendation."
        ))

      case _ =>
        for {
          profileVecOpt <- qdrantClient.getEmployeeProfile(nomineeId)

          result <- profileVecOpt match {
            case None =>
              Future.successful(Left(
                "Employee profile vector not found. This usually means no appreciations " +
                  "have been processed through the intelligence pipeline yet. " +
                  "Please wait for at least one appreciation to be fully indexed."
              ))

            case Some(profileVector) =>
              runRecommendation(nomineeId, requestedBy, companyId, departmentId, profileVector)
          }
        } yield result
    }
  }

  def getRecommendationHistory(nomineeId: String): Future[Seq[AwardRecommendation]] =
    awardRecommendationRepo.findByNominee(nomineeId)

  private def runRecommendation(
                                 nomineeId:     String,
                                 requestedBy:   String,
                                 companyId:     String,
                                 departmentId:  String,
                                 profileVector: Seq[Float]
                               ): Future[Either[String, RecommendationResult]] = {
    qdrantClient.searchAwardDefinitions(
      profileVector = profileVector,
      companyId     = companyId,
      departmentId  = departmentId,
      limit         = TopN * 3
    ).flatMap { awardPoints =>

      val candidates = awardPoints
        .filter(_.getScore >= ScoreThreshold)
        .sortBy(-_.getScore)
        .take(TopN)
        .flatMap { sp =>
          val payload = sp.getPayload
          for {
            awardId      <- Option(payload.get("award_id")).map(_.getStringValue).filter(_.nonEmpty)
            awardName    <- Option(payload.get("award_name")).map(_.getStringValue).filter(_.nonEmpty)
            criteriaText <- Option(payload.get("criteria_text")).map(_.getStringValue).filter(_.nonEmpty)
            criteriaVec  <- Option(sp.getVectors)
              .map(_.getVector.getDataList.asScala.toSeq.map(_.floatValue()))
              .filter(_.nonEmpty)
          } yield (awardId, awardName, criteriaText, criteriaVec, sp.getScore)
        }

      if (candidates.isEmpty)
        return Future.successful(Left(
          s"No awards matched the employee's profile above the required score threshold ($ScoreThreshold). " +
            "Consider adding more award definitions or ensuring the employee has richer appreciation history."
        ))

      val evidenceFutures: List[Future[MatchedAward]] = candidates.toList.map {
        case (awardId, awardName, criteriaText, criteriaVec, score) =>
          qdrantClient.searchForNominee(
            queryVector    = criteriaVec,
            nomineeId      = nomineeId,
            limit          = EvidencePerAward,
            scoreThreshold = 0.0f
          ).map { points =>
            MatchedAward(
              awardId        = awardId,
              awardName      = awardName,
              criteriaText   = criteriaText,
              criteriaVector = criteriaVec,
              score          = score,
              evidence       = extractMessages(points)
            )
          }.recover { case e =>
            logger.warn(
              s"[AwardRecommendationService] Evidence fetch failed for award $awardId: ${e.getMessage}"
            )
            // Return award with empty evidence rather than failing the whole result
            MatchedAward(awardId, awardName, criteriaText, criteriaVec, score, Nil)
          }
      }

      Future.sequence(evidenceFutures).flatMap { matchedAwards =>

        generateValidationParagraphs(matchedAwards).flatMap { validations =>

          val rankedAwards: List[RankedAward] = matchedAwards.zipWithIndex.map {
            case (award, idx) =>
              RankedAward(
                awardId      = award.awardId,
                awardName    = award.awardName,
                score        = award.score,
                criteriaText = award.criteriaText
              )
          }

          val profileSummary = validations

          val result = RecommendationResult(
            nomineeId      = nomineeId,
            profileSummary = profileSummary,
            rankedAwards   = rankedAwards
          )

          cacheResult(result, requestedBy).map(_ => Right(result))
        }
      }
    }.recover { case e =>
      Left(s"Award search failed: ${e.getMessage}")
    }
  }

  private def generateValidationParagraphs(awards: List[MatchedAward]): Future[String] = {

    if (awards.isEmpty) return Future.successful("")

    val awardBlocks = awards.zipWithIndex.map { case (award, idx) =>
      val evidenceLines = if (award.evidence.isEmpty)
        "  (no specific messages retrieved)"
      else
        award.evidence.zipWithIndex
          .map { case (msg, i) => s"  ${i + 1}. \"${msg.take(150)}\"" }
          .mkString("\n")

      s"""Award ${idx + 1}: ${award.awardName}
         |Criteria: ${award.criteriaText.take(200)}
         |Relevant appreciations:
         |$evidenceLines""".stripMargin
    }.mkString("\n\n")

    val systemPrompt =
      "You are an HR analyst writing award nomination justifications. Be specific and concise. Return only JSON."

    val userPrompt =
      s"""Based on these peer recognition messages, write a 1-2 sentence justification
         |for why each award fits this employee.
         |
         |$awardBlocks
         |
         |Return ONLY this JSON, no other text:
         |{"justifications":["justification for award 1","justification for award 2","justification for award 3"]}
         |""".stripMargin

    geminiClient.generateContent(systemPrompt, userPrompt, temperature = 0.1).map {
      case Left(err) =>
        logger.warn(s"[AwardRecommendationService] Validation LLM call failed (non-fatal): $err")
        // Degrade gracefully — return a plain summary without LLM justification
        awards.map(a => s"${a.awardName} (${math.round(a.score * 100)}% match)").mkString("; ")

      case Right(raw) =>
        try {
          val json = Json.parse(raw)
          (json \ "justifications").asOpt[List[String]] match {
            case Some(justifications) if justifications.nonEmpty =>
              awards.zip(justifications).map { case (award, just) =>
                s"${award.awardName}: $just"
              }.mkString("\n\n")
            case _ =>
              awards.map(a => s"${a.awardName} (${math.round(a.score * 100)}% match)").mkString("; ")
          }
        } catch {
          case _: Exception =>
            awards.map(a => s"${a.awardName} (${math.round(a.score * 100)}% match)").mkString("; ")
        }
    }
  }

  private def cacheResult(result: RecommendationResult, requestedBy: String): Future[Unit] = {
    val rankedJson = Json.toJson(result.rankedAwards).toString()
    val rec = AwardRecommendation(
      id                = UUID.randomUUID().toString,
      nomineeId         = result.nomineeId,
      requestedBy       = requestedBy,
      recommendedAwards = rankedJson,
      profileSummary    = result.profileSummary
    )
    awardRecommendationRepo.insert(rec).map(_ => ()).recover { case e =>
      logger.error(s"[AwardRecommendationService] Failed to cache recommendation: ${e.getMessage}")
    }
  }

  private def extractMessages(points: List[io.qdrant.client.grpc.Points.ScoredPoint]): List[String] =
    points.flatMap { p =>
      Option(p.getPayload.get("message_preview")).map(_.getStringValue).filter(_.nonEmpty)
    }
}