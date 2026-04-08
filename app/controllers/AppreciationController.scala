package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import services.AppreciationService
import actions.{AuthAction, AuthHelper}
import models.Role

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AppreciationController @Inject()(cc: ControllerComponents, appreciationService: AppreciationService, authAction: AuthAction)(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  def createAppreciation = authAction.async(parse.json) { request =>

    AuthHelper.authorize[JsValue](
      Set("ADMIN", "HR", "DEPARTMENT_MANAGER", "TEAM_LEAD").map(Role.fromString)
    ) { req =>

      val receiverId = (req.body \ "receiverId").as[String]
      val text = (req.body \ "text").as[String]
      val skillTags = (req.body \ "skillTags").asOpt[Seq[String]].getOrElse(Seq.empty)
      val appreciationType = (req.body \ "appreciationType").as[String]
      val visibility = (req.body \ "visibility").as[String]

      appreciationService.createAppreciation(
        req.user,
        receiverId,
        text,
        skillTags,
        appreciationType,
        visibility
      ).map {
        case Right(res) => Ok(Json.obj(
          "data" -> Json.obj(
            "id" -> res.appreciation.id,
            "giverId" -> res.appreciation.giverId,
            "receiverId" -> res.appreciation.receiverId,
            "text" -> res.appreciation.text,
            "appreciationType" -> res.appreciation.appreciationType,
            "visibility" -> res.appreciation.visibility,
            "createdAt" -> res.appreciation.createdAt,
            "giverName" -> res.giverName,
            "receiverName" -> res.receiverName,
            "skillTags" -> res.skillTags.map { tag =>
              Json.obj(
                "id" -> tag.id,
                "skillName" -> tag.skillName,
                "confidenceScore" -> tag.confidenceScore,
                "source" -> tag.source
              )
            }
          )
        ))
        case Left(err) => BadRequest(Json.obj("error" -> err))
      }
    }(ec)(request)
  }
}