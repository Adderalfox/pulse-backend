package services

import models.AppreciationSkill
import repositories.AppreciationSkillRepository
import play.api.Logging

import java.util.UUID
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IntelligencePipeline @Inject()(extractionService: SkillExtractionService, skillService: SkillService, scoringService: SkillScoringService, appreciationSkillRepo: AppreciationSkillRepository, embeddingService: EmbeddingService)(implicit ec: ExecutionContext) extends Logging {
  def process(appreciationId: String, message: String, recipientId: String, senderId: String, departmentId: String, senderRole: String, recipientRole: String, department: String): Future[Unit] = {
    logger.info(s"[IntelligencePipeline] Starting for appreciation=$appreciationId")

    extractionService.extract(message, senderRole, recipientRole, department)
      .flatMap { extraction =>
        logger.info(
          s"[IntelligencePipeline] Extracted ${extraction.skills.size} skills " +
            s"via ${extraction.source} for appreciation=$appreciationId"
        )

        skillService.resolveSkills(extraction.skills).flatMap { resolvedSkills =>
          val appreciationSkillRows = resolvedSkills.map { case (skill, confidence) =>
            AppreciationSkill(
              id = UUID.randomUUID().toString,
              appreciationId = appreciationId,
              skillId = skill.id,
              llmConfidence = confidence,
              extractionModel = extraction.modelUsed
            )
          }

          val persistSkillsF = appreciationSkillRepo.insertAll(appreciationSkillRows)
            .recover { e =>
              logger.error(s"[IntelligencePipeline] Failed to persist skills for $appreciationId: ${e.getMessage}")
            }

          val scoreUpdateF = scoringService.updateScores(recipientId = recipientId, appreciatorId = senderId, skills = resolvedSkills).recover { e =>
            logger.error(s"[IntelligencePipeline] Failed to update scores for ${e.getMessage}")
          }

          val embeddingF = embeddingService.embedAppreciation(
            appreciationId = appreciationId,
            recipientId = recipientId,
            senderId = senderId,
            departmentId = departmentId,
            message = message,
            extractedSkills = resolvedSkills.map(_._1.normalizedName)
          ).recover { e =>
            logger.error(s"[IntelligencePipeline] Embedding failed for $appreciationId: ${e.getMessage}")
          }

          for {
            _ <- persistSkillsF
            _ <- scoreUpdateF
            _ <- embeddingF
          } yield logger.info(s"[IntelligencePipeline] Completed for appreciation=$appreciationId")
        }
      }
      .recover { e =>
        logger.error(s"[IntelligencePipeline] Top-level failure for $appreciationId: ${e.getMessage}", e)
      }
  }
}