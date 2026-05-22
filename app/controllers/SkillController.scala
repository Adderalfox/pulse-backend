package controllers

import play.api.mvc._
import play.api.libs.json._
import repositories.{SkillRepository, UserSkillRepository}
import actions.{AuthAction, AuthHelper}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SkillController @Inject()(cc: ControllerComponents, skillRepo: SkillRepository, userSkillRepo: UserSkillRepository, authAction: AuthAction)(implicit ec: ExecutionContext) extends AbstractController(cc) {
  def getUserSkills(userId: String) = authAction.async { implicit request =>
    AuthHelper.authorize[AnyContent](Set("EMPLOYEE", "TEAM_LEAD", "HR", "ADMIN", "SUPER_ADMIN").map(models.Role.fromString)) {
      _ =>
        userSkillRepo.getTopSkillsForUser(userId, 10).flatMap { userSkills =>
          val skillIdToScore = userSkills.map(us => us.skillId -> us).toMap
          Future.sequence(userSkills.map(us => skillRepo.findById(us.skillId))).map { skillOpts =>
            val payload = skillOpts.flatten.map { skill =>
              val us = skillIdToScore(skill.id)
              Json.obj(
                "skillId" -> skill.id,
                "skillName" -> skill.name,
                "category" -> skill.category.name,
                "compositeScore" -> us.compositeScore,
                "frequencyCount" -> us.frequencyCount,
                "appreciatorDiversity" -> us.appreciatorDiversity
              )
            }
            Ok(Json.obj("userId" -> userId, "skills" -> JsArray(payload)))
          }
        }
    }(ec)(request)
  }

  def getTopUsersForSkill = authAction.async { implicit request =>
    AuthHelper.authorize[AnyContent](Set("HR", "ADMIN", "SUPER_ADMIN").map(models.Role.fromString)) {
      req =>
        val skillName = req.getQueryString("skill").getOrElse("").toLowerCase
        val limit = req.getQueryString("limit").flatMap(_.toIntOption).getOrElse(10)
        skillRepo.findByNormalizedName(skillName).flatMap {
          case None => Future.successful(NotFound(Json.obj("error" -> s"Skill '$skillName' not found")))
          case Some(skill) =>
            userSkillRepo.getTopUsersForSkill(skill.id, limit).map { rows =>
              Ok(Json.obj(
                "skill" -> skill.name,
                "topUsers" -> JsArray(rows.map { us =>
                  Json.obj(
                    "userId" -> us.userId,
                    "compositeScore" -> us.compositeScore,
                    "frequencyCount" -> us.frequencyCount,
                    "appreciatorDiversity" -> us.appreciatorDiversity
                  )
                })
              ))
            }
        }
    }(ec)(request)
  }

  def searchSkills = authAction.async { implicit request =>
    AuthHelper.authorize[AnyContent](Set("EMPLOYEE", "TEAM_LEAD", "HR", "ADMIN", "SUPER_ADMIN").map(models.Role.fromString)) {
      req =>
        val q = req.getQueryString("q").getOrElse("").toLowerCase
        skillRepo.searchByName(q).map { skills =>
          Ok(Json.obj("results" -> JsArray(skills.map { s =>
            Json.obj("id" -> s.id, "name" -> s.name, "category" -> s.category.name)
          })))
        }
    }(ec)(request)
  }
}

