package services

import models.AppreciationSkill
import repositories.AppreciationSkillRepository
import play.api.Logging

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IntelligencePipeline @Inject()(
                                      extractionService: SkillExtractionService,
                                      skillService:      SkillService,
                                      scoringService:    SkillScoringService,
                                      embeddingService:  EmbeddingService,
                                      appreciationSkillRepo: AppreciationSkillRepository
                                    )(implicit ec: ExecutionContext) extends Logging {

  def process(
               appreciationId: String,
               message:        String,
               recipientId:    String,
               senderId:       String,
               departmentId:   String,
               senderRole:     String,
               recipientRole:  String,
               department:     String
             ): Future[Unit] = {
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
              id             = UUID.randomUUID().toString,
              appreciationId = appreciationId,
              skillId        = skill.id,
              llmConfidence  = confidence,
              extractionModel = extraction.modelUsed
            )
          }

          val persistSkillsF = appreciationSkillRepo.insertAll(appreciationSkillRows)
            .recover { case e =>
              logger.error(s"[IntelligencePipeline] Failed to persist skills for $appreciationId: ${e.getMessage}")
            }

          val scoreUpdateF = scoringService
            .updateScores(recipientId = recipientId, appreciatorId = senderId, skills = resolvedSkills)
            .recover { case e =>
              logger.error(s"[IntelligencePipeline] Score update failed: ${e.getMessage}")
            }

          // Step 1 — embed the appreciation and upsert into the appreciations pool.
          // Step 2 — once we have the vector back, update the employee's rolling
          //           centroid profile.  Profile update is chained on embedF so
          //           it runs only after the individual embedding succeeds and we
          //           can reuse the same vector without a second embed call.
          //
          // Both failures are non-fatal: .recover ensures a pipeline failure
          // never propagates back to the appreciation creation response.
          val embeddingAndProfileF: Future[Unit] =
            embeddingService.embedAndReturnVector(
              appreciationId  = appreciationId,
              recipientId     = recipientId,
              senderId        = senderId,
              departmentId    = departmentId,
              message         = message,
              extractedSkills = resolvedSkills.map(_._1.normalizedName)
            ).flatMap {
              case None =>
                // embedAppreciation logged the error already; skip profile update
                Future.successful(())
              case Some(vector) =>
                // Reuse the same vector — no second embed call to Ollama/Gemini
                embeddingService.updateEmployeeProfileVector(
                  employeeId = recipientId,
                  newVector  = vector
                ).recover { case e =>
                  logger.error(
                    s"[IntelligencePipeline] Profile vector update failed for $recipientId " +
                      s"(non-fatal): ${e.getMessage}"
                  )
                }
            }.recover { case e =>
              logger.error(s"[IntelligencePipeline] Embedding failed for $appreciationId: ${e.getMessage}")
            }

          for {
            _ <- persistSkillsF
            _ <- scoreUpdateF
            _ <- embeddingAndProfileF
          } yield logger.info(s"[IntelligencePipeline] Completed for appreciation=$appreciationId")
        }
      }
      .recover { case e =>
        logger.error(s"[IntelligencePipeline] Top-level failure for $appreciationId: ${e.getMessage}", e)
      }
  }
}