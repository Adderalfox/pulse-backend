package services

import models.AwardDefinition
import repositories.AwardDefinitionRepository
import utils.{EmbeddingTaskType, GeminiClient, QdrantClientWrapper}
import play.api.Logging

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AwardDefinitionService @Inject()(awardDefinitionRepo: AwardDefinitionRepository, geminiClient: GeminiClient, qdrantClient: QdrantClientWrapper
                                      )(implicit ec: ExecutionContext) extends Logging {

  def createAwardDefinition(
                             companyId: String,
                             departmentId: Option[String],
                             name: String,
                             description: String,
                             criteriaText: String,
                             createdBy: String
                           ): Future[Either[String, AwardDefinition]] = {

    val award = AwardDefinition(
      id = UUID.randomUUID().toString,
      companyId = companyId,
      departmentId = departmentId,
      name = name,
      description = description,
      criteriaText = criteriaText,
      createdBy = createdBy
    )

    awardDefinitionRepo.insert(award).flatMap { saved =>
      embedAndUpsert(saved).map {
        case Right(_) =>
          logger.info(s"[AwardDefinitionService] Award '${saved.name}' (${saved.id}) embedded and stored.")
          Right(saved)
        case Left(err) =>
          // Postgres row is saved; Qdrant embedding failed — log and surface the error
          // so the caller can decide whether to retry. The DB row is not rolled back
          // because it is still useful for listing/CRUD; it just won't appear in
          // recommendations until re-embedded (a future re-sync endpoint can fix this).
          logger.error(s"[AwardDefinitionService] Qdrant upsert failed for award ${saved.id}: $err")
          Left(s"Award saved but embedding failed: $err")
      }
    }.recover { case e =>
      logger.error(s"[AwardDefinitionService] DB insert failed: ${e.getMessage}")
      Left(s"Failed to create award definition: ${e.getMessage}")
    }
  }

  def getById(id: String): Future[Either[String, AwardDefinition]] =
    awardDefinitionRepo.findById(id).map {
      case Some(a) => Right(a)
      case None => Left(s"Award definition $id not found")
    }

  def listByCompany(companyId: String): Future[Either[String, Seq[AwardDefinition]]] =
    awardDefinitionRepo.findByCompany(companyId)
      .map(Right(_))
      .recover { case e => Left(e.getMessage) }

  def listByCompanyAndDepartment(
                                  companyId: String,
                                  departmentId: String
                                ): Future[Either[String, Seq[AwardDefinition]]] =
    awardDefinitionRepo.findByCompanyAndDepartment(companyId, departmentId)
      .map(Right(_))
      .recover { case e => Left(e.getMessage) }

  def updateAwardDefinition(
                             id: String,
                             name: String,
                             description: String,
                             criteriaText: String,
                             departmentId: Option[String],
                             requesterId: String
                           ): Future[Either[String, AwardDefinition]] =
    awardDefinitionRepo.findById(id).flatMap {
      case None => Future.successful(Left(s"Award definition $id not found"))
      case Some(existing) =>
        val updated = existing.copy(
          name = name,
          description = description,
          criteriaText = criteriaText,
          departmentId = departmentId
        )
        awardDefinitionRepo.update(updated).flatMap { _ =>
          embedAndUpsert(updated).map {
            case Right(_) => Right(updated)
            case Left(err) =>
              logger.error(s"[AwardDefinitionService] Re-embed failed for award $id: $err")
              Left(s"Award updated but re-embedding failed: $err")
          }
        }.recover { case e => Left(e.getMessage) }
    }

  def deleteAwardDefinition(id: String): Future[Either[String, Unit]] =
    awardDefinitionRepo.delete(id).map {
      case 0 => Left(s"Award definition $id not found")
      case _ => Right(())
    }.recover { case e => Left(e.getMessage) }

  // Embed criteria_text and upsert to pulse_award_definitions
  private def embedAndUpsert(award: AwardDefinition): Future[Either[String, Unit]] =
    geminiClient.embedText(award.criteriaText, EmbeddingTaskType.RETRIEVAL_DOCUMENT).flatMap {
      case Left(err) =>
        Future.successful(Left(s"Embedding failed: $err"))
      case Right(vector) =>
        // department_id stored as "" when company-wide so Qdrant filter can match it
        val deptPayload = award.departmentId.getOrElse("")
        val payload = Map(
          "award_id" -> award.id,
          "company_id" -> award.companyId,
          "department_id" -> deptPayload,
          "award_name" -> award.name,
          "criteria_text" -> award.criteriaText
        )
        qdrantClient.upsertAwardDefinition(award.id, vector, payload)
          .map(_ => Right(()))
          .recover { case e => Left(e.getMessage) }
    }
}