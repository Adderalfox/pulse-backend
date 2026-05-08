package services

import models.EmbeddingSyncLog
import repositories.EmbeddingSyncLogRepository
import utils.{AppConfig, EmbeddingTaskType, GeminiClient, QdrantClientWrapper}
import play.api.Logging

import java.util.UUID
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmbeddingService @Inject()(geminiClient: GeminiClient, qdrantClient: QdrantClientWrapper, syncLogRepo: EmbeddingSyncLogRepository, config: AppConfig)(implicit ec: ExecutionContext) extends Logging {
  def embedAppreciation(appreciationId: String, recipientId: String, senderId: String, departmentId: String, message: String, extractedSkills: List[String]): Future[Unit] = {
    syncLogRepo.exists(appreciationId).flatMap {
      case true =>
        logger.debug(s"Appreciation $appreciationId already embedded, skipping")
        Future.successful(())
      case false =>
        val enrichedText = buildEmbeddingText(message, extractedSkills)
        geminiClient.embedText(enrichedText, EmbeddingTaskType.RETRIEVAL_DOCUMENT).flatMap {
          case Left(err) =>
            logger.error(s"Embedding failed for appreciation $appreciationId: $err")
            Future.successful(())
          case Right(vector) =>
            logger.info(s"Successfully reached Gemini model for embedding appreciation $appreciationId")
            val pointId = UUID.randomUUID().toString
            val payload = Map(
              "appreciation_id" -> appreciationId,
              "recipient_id" -> recipientId,
              "sender_id" -> senderId,
              "department_id" -> departmentId,
              "message_preview" -> message.take(200),
              "extracted_skills" -> extractedSkills.mkString(","),
              "embedded_at" -> java.time.LocalDateTime.now().toString
            )
            for {
              _ <- qdrantClient.upsertPoint(pointId, vector, payload)
              _ <- syncLogRepo.insert(EmbeddingSyncLog(
                appreciationId = appreciationId,
                qdrantPointId = pointId,
                modelVersion = config.gemini.embeddingModel
              ))
            } yield ()
        }
    }
  }

  private def buildEmbeddingText(message: String, skills: List[String]): String =
    s"""Skills demonstrated: ${skills.mkString(", ")}.
       |Recognition message: $message""".stripMargin
}