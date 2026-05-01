package controllers

import play.api.mvc._
import play.api.libs.json._
import services.NominationService
import actions.{AuthAction, AuthHelper}

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NominationController @Inject()(cc: ControllerComponents, nominationService: NominationService, authAction: AuthAction)(implicit ec: ExecutionContext) extends AbstractController(cc) {
  def generateDraft = authAction.async(parse.json) {implicit request =>
    AuthHelper.authorize[JsValue](
      Set("TEAM_LEAD", "HR", "ADMIN", "SUPER_ADMIN").map(models.Role.fromString)
    ) { req =>
      val nomineeId = (req.body \ "nomineeId").as[String]
      val awardCategory = (req.body \ "awardCategory").as[String]
      val nomineeName = (req.body \ "nomineeName").as[String]
      val nomineeDepartment = (req.body \ "nomineeDepartment").as[String]
      val requestedBy = req.user.userId

      nominationService.generateDraft(
        nomineeId = nomineeId,
        requestedBy = requestedBy,
        awardCategory = awardCategory,
        nomineeName = nomineeName,
        nomineeDepartment = nomineeDepartment
      ).map {
        case Right(draft) =>
          Ok(Json.obj(
            "id" -> draft.id,
            "nomineeId"        -> draft.nomineeId,
            "awardCategory"    -> draft.awardCategory,
            "draftText"        -> draft.draftText,
            "skillsCited"      -> draft.skillsCited.split(",").filter(_.nonEmpty).toList,
            "appreciationsUsed" -> draft.appreciationsUsed,
            "generatedAt"      -> draft.generatedAt.map(_.toString)
          ))
        case Left(err) =>
          UnprocessableEntity(Json.obj("error" -> err))
      }
    }(ec)(request)
  }

  def getDraftsForNominee(nomineeId: String) = authAction.async { implicit request =>
    AuthHelper.authorize[AnyContent](
      Set("TEAM_LEAD", "HR", "ADMIN", "SUPER_ADMIN").map(models.Role.fromString)
    ) { _ =>
      nominationService.getDraftsForNominee(nomineeId).map { drafts =>
        Ok(Json.obj("drafts" -> JsArray(drafts.map { d =>
          Json.obj(
            "id"               -> d.id,
            "awardCategory"    -> d.awardCategory,
            "draftText"        -> d.draftText,
            "skillsCited"      -> d.skillsCited.split(",").filter(_.nonEmpty).toList,
            "appreciationsUsed" -> d.appreciationsUsed,
            "generatedAt"      -> d.generatedAt.map(_.toString)
          )
        })))
      }
    }(ec)(request)
  }

  def getMyDrafts = authAction.async { implicit request =>
    AuthHelper.authorize[AnyContent](
      Set("TEAM_LEAD", "HR", "ADMIN", "SUPER_ADMIN").map(models.Role.fromString)
    ) { req =>
      nominationService.getDraftsByRequester(req.user.userId).map { drafts =>
        Ok(Json.obj("drafts" -> JsArray(drafts.map { d =>
          Json.obj(
            "id"               -> d.id,
            "nomineeId"        -> d.nomineeId,
            "awardCategory"    -> d.awardCategory,
            "draftText"        -> d.draftText,
            "appreciationsUsed" -> d.appreciationsUsed,
            "generatedAt"      -> d.generatedAt.map(_.toString)
          )
        })))
      }
    }(ec)(request)
  }
}