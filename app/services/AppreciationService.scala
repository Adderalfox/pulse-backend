package services

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import repositories.{AppreciationRepository, AppreciationSkillTagRepository, InteractionEdgeRepository, UserRepository, UserSkillScoreRepository}
import models.{Appreciation, AppreciationSkillTag, AppreciationWithDetails, InteractionEdge, JwtPayload, Role, UserSkillScore}
import org.postgresql.util.PSQLException

import java.util.UUID

@Singleton
class AppreciationService @Inject()(userRepo: UserRepository, appreciationRepo: AppreciationRepository, interactionRepo: InteractionEdgeRepository, skillTagRepo: AppreciationSkillTagRepository, skillScoreRepo: UserSkillScoreRepository)(implicit ec: ExecutionContext) {

  def createAppreciation(requester: JwtPayload, receiverId: String, text: String, skillTags: Seq[String], appreciationType: String, visibility: String): Future[Either[String, AppreciationWithDetails]] = {
    val allowed = requester.role match {
      case Role.EMPLOYEE => Set("peer")
      case Role.TEAM_LEAD => Set("peer", "lead_recognition")
      case Role.DEPARTMENT_MANAGER | Role.ADMIN | Role.HR => Set("peer", "lead_recognition", "manager_nomination")
      case _ => Set.empty[String]
    }

    if (!allowed.contains(appreciationType))
      return Future.successful(Left("Invalid appreciation type for your role"))

    val app = Appreciation(
      id = UUID.randomUUID().toString,
      giverId = requester.userId,
      receiverId = receiverId,
      companyId = requester.companyId.get,
      departmentId = requester.departmentId,
      text = text,
      appreciationType = appreciationType,
      visibility = visibility
    )
    println("DepartmentId for requester" -> app.departmentId)

    appreciationRepo.create(app).flatMap { saved =>
      interactionRepo.upsert(
        InteractionEdge(
          id = UUID.randomUUID().toString,
          userIdFrom = requester.userId,
          userIdTo = receiverId,
          companyId = requester.companyId.get,
          interactionType = "appreciated"
        )
      ).recover { case _ => () }

      if (skillTags.nonEmpty) {
        val tags = skillTags.map { skill =>
          AppreciationSkillTag(
            id = UUID.randomUUID().toString,
            appreciationId = saved.id,
            skillName = skill
          )
        }
        skillTagRepo.createMany(tags).recover { case _ => Seq.empty }

        tags.foreach { tag =>
          val delta = appreciationType match {
            case "manager nomination" => 2.0
            case "lead_recognition" => 1.5
            case _ => 1.0
          }

          skillScoreRepo.upsert(
            UserSkillScore(
              id = UUID.randomUUID().toString,
              userId = receiverId,
              companyId = requester.companyId.get,
              skillName = tag.skillName,
              score = delta,
              endorsementCount = 1
            )
          )
        }
      }
      for {
        giverOpt <- userRepo.findById(saved.giverId)
        receiverOpt <- userRepo.findById(saved.receiverId)
        tags <- skillTagRepo.getByAppreciation(saved.id)
      } yield {
        Right(
          AppreciationWithDetails(
            saved,
            giverOpt.map(_.name).getOrElse("Unknown"),
            giverOpt.map(_.role).getOrElse(Role.EMPLOYEE),
            receiverOpt.map(_.name).getOrElse("Unknown"),
            receiverOpt.map(_.role).getOrElse(Role.EMPLOYEE),
            tags
          )
        )
      }
    }.recover {
      case ex: PSQLException => Left("Database error")
    }
  }
}