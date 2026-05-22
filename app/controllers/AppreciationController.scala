package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import services.{AppreciationService, FeedService, IntelligencePipeline, SuggestionService}
import actions.{AuthAction, AuthHelper}
import models.Role

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AppreciationController @Inject()(cc: ControllerComponents, appreciationService: AppreciationService, feedService: FeedService, suggestionService: SuggestionService, intelligencePipeline: IntelligencePipeline, authAction: AuthAction)(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  def createAppreciation = authAction.async(parse.json) { request =>

    AuthHelper.authorize[JsValue](
      Set("ADMIN", "HR", "DEPARTMENT_MANAGER", "TEAM_LEAD", "EMPLOYEE").map(Role.fromString)
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
        case Right(res) =>
          intelligencePipeline.process(
            appreciationId = res.appreciation.id,
            message = res.appreciation.text,
            recipientId = res.appreciation.receiverId,
            senderId = res.appreciation.giverId,
            departmentId = "",
            senderRole = req.user.role.name,
            recipientRole = "EMPLOYEE",
            department = "Engineering"
          )
          Ok(Json.obj(
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

  def feed(limit: Int, offset: Int) = authAction.async { request =>
    AuthHelper.authorize[AnyContent](
      Set("EMPLOYEE", "TEAM_LEAD", "DEPARTMENT_MANAGER", "ADMIN", "HR", "SUPER_ADMIN").map(Role.fromString)
    ) { req =>

      feedService.getFeed(req.user, limit, offset).map {
        case Right(feed) =>
          Ok(Json.obj(
            "feed" -> feed.map(f => Json.obj(
              "id" -> f.appreciation.id,
              "text" -> f.appreciation.text,
              "giverName" -> f.giverName,
              "receiverName" -> f.receiverName,
              "skillTags" -> f.skillTags.map(t =>
              Json.obj("skillName" -> t.skillName))
            ))
          ))

        case Left(err) =>
          BadRequest(Json.obj("error" -> err))
      }
    }(ec)(request)
  }

  def suggestions = authAction.async { request =>
    AuthHelper.authorize[AnyContent](
      Set("EMPLOYEE", "TEAM_LEAD", "DEPARTMENT_MANAGER", "ADMIN", "HR", "SUPER_ADMIN").map(Role.fromString)
    ) { req =>

      suggestionService.getSuggestionsForUser(req.user).map {
        case Right(users) =>
          Ok(Json.obj(
            "suggestions" -> users.map(u =>
            Json.obj(
              "id" -> u.id,
              "name" -> u.name,
              "departmentId" -> u.departmentId
            ))
          ))

        case Left(err) =>
          BadRequest(Json.obj("error" -> err))
      }
    }(ec)(request)
  }
}