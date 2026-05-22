package services

import models.EmbeddingSyncLog
import repositories.{AppreciationRepository, EmbeddingSyncLogRepository}
import utils.{AppConfig, EmbeddingTaskType, GeminiClient, QdrantClientWrapper}
import play.api.Logging

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmbeddingService @Inject()(
                                  geminiClient:     GeminiClient,
                                  qdrantClient:     QdrantClientWrapper,
                                  syncLogRepo:      EmbeddingSyncLogRepository,
                                  appreciationRepo: AppreciationRepository,
                                  config:           AppConfig
                                )(implicit ec: ExecutionContext) extends Logging {

  def embedAppreciation(
                         appreciationId:  String,
                         recipientId:     String,
                         senderId:        String,
                         departmentId:    String,
                         message:         String,
                         extractedSkills: List[String]
                       ): Future[Unit] =
    embedAndReturnVector(appreciationId, recipientId, senderId, departmentId, message, extractedSkills)
      .map(_ => ())

  def embedAndReturnVector(
                            appreciationId:  String,
                            recipientId:     String,
                            senderId:        String,
                            departmentId:    String,
                            message:         String,
                            extractedSkills: List[String]
                          ): Future[Option[Seq[Float]]] = {
    syncLogRepo.exists(appreciationId).flatMap {
      case true =>
        logger.debug(s"Appreciation $appreciationId already embedded, skipping")
        Future.successful(None)

      case false =>
        val enrichedText = buildEmbeddingText(message, extractedSkills)
        geminiClient.embedText(enrichedText, EmbeddingTaskType.RETRIEVAL_DOCUMENT).flatMap {
          case Left(err) =>
            logger.error(s"[EmbeddingService] Embed failed for $appreciationId: $err")
            Future.successful(None)

          case Right(vector) =>
            logger.info(s"[EmbeddingService] Embedded appreciation $appreciationId")
            val pointId = UUID.randomUUID().toString
            val payload = Map(
              "appreciation_id"  -> appreciationId,
              "recipient_id"     -> recipientId,
              "sender_id"        -> senderId,
              "department_id"    -> departmentId,
              "message_preview"  -> message.take(200),
              "extracted_skills" -> extractedSkills.mkString(","),
              "embedded_at"      -> java.time.LocalDateTime.now().toString
            )
            for {
              _ <- qdrantClient.upsertPoint(pointId, vector, payload)
              _ <- syncLogRepo.insert(EmbeddingSyncLog(
                appreciationId = appreciationId,
                qdrantPointId  = pointId,
                modelVersion   = config.gemini.embeddingModel
              ))
            } yield Some(vector)
        }
    }
  }

  def updateEmployeeProfileVector(
                                   employeeId: String,
                                   newVector:  Seq[Float]
                                 ): Future[Unit] = {

    val existingF = qdrantClient.getEmployeeProfile(employeeId)
    val countF    = appreciationRepo.countForUser(employeeId)

    for {
      existing   <- existingF
      totalCount <- countF

      newCentroid = computeRollingCentroid(
        existing = existing,
        newVec   = newVector,
        newCount = totalCount
      )

      payload = Map(
        "employee_id"        -> employeeId,
        "appreciation_count" -> totalCount.toString,
        "last_updated"       -> java.time.LocalDateTime.now().toString
      )

      _ <- qdrantClient.upsertEmployeeProfile(employeeId, newCentroid, payload)
    } yield {
      logger.info(
        s"[EmbeddingService] Profile centroid updated for employee $employeeId " +
          s"(total appreciations: $totalCount)"
      )
    }
  }

  private def computeRollingCentroid(
                                      existing: Option[Seq[Float]],
                                      newVec:   Seq[Float],
                                      newCount: Long
                                    ): Seq[Float] = existing match {

    case None =>
      newVec

    case Some(old) if old.size != newVec.size =>
      logger.warn(
        s"[EmbeddingService] Centroid dimension mismatch " +
          s"(stored=${old.size}, new=${newVec.size}). Resetting profile vector."
      )
      newVec

    case Some(old) =>
      val oldCount = newCount - 1
      old.zip(newVec).map { case (o, n) =>
        ((o * oldCount) + n) / newCount
      }
  }

  private def buildEmbeddingText(message: String, skills: List[String]): String =
    s"""Skills demonstrated: ${skills.mkString(", ")}.
       |Recognition message: $message""".stripMargin
}