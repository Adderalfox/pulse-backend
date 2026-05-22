package controllers

import play.api.mvc._
import play.api.libs.json._
import services.{InsightService, RagService}
import actions.{AuthAction, AuthHelper}

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class InsightController @Inject()(cc: ControllerComponents, insightService: InsightService, ragService: RagService, authAction: AuthAction)(implicit ec: ExecutionContext) extends AbstractController(cc) {
  def queryExperts = authAction.async(parse.json) { implicit request =>
    AuthHelper.authorize[JsValue](Set("EMPLOYEE", "TEAM_LEAD", "HR", "ADMIN", "SUPER_ADMIN").map(models.Role.fromString)) {
      req =>
        val query = (req.body \ "query").as[String]
        val limit = (req.body \ "limit").asOpt[Int].getOrElse(5)
        ragService.findExpertsForQuery(query, limit).map {
          case Right(result) =>
            Ok(Json.obj(
              "query" -> result.query,
              "extractedSkills" -> result.extractedSkills,
              "experts" -> JsArray(result.experts.map { e =>
                Json.obj(
                  "userId" -> e.userId,
                  "displayName" -> e.displayName,
                  "compositeScore" -> e.compositeScore,
                  "frequencyCount" -> e.frequencyCount,
                  "appreciatorDiversity" -> e.appreciatorDiversity,
                  "explanation" -> e.explanation
                )
              })
            ))
          case Left(err) => InternalServerError(Json.obj("error" -> err))
        }
    }(ec)(request)
  }

  def getTopExperts = authAction.async { implicit request =>
    AuthHelper.authorize[AnyContent](Set("HR", "ADMIN", "SUPER_ADMIN").map(models.Role.fromString)) {
      req =>
        val skill = req.getQueryString("skill").getOrElse("")
        val limit = req.getQueryString("limit").flatMap(_.toIntOption).getOrElse(10)
        insightService.getTopExperts(skill, Map.empty, limit).map {
          case Right(experts) =>
            Ok(Json.obj("skill" -> skill, "experts" -> JsArray(experts.map { e =>
              Json.obj(
                "userId" -> e.userId,
                "compositeScore" -> e.compositeScore,
                "frequencyCount" -> e.frequencyCount,
                "explanation" -> e.explanation
              )
            })))
          case Left(err) => NotFound(Json.obj("error" -> err))
        }
    }(ec)(request)
  }
}