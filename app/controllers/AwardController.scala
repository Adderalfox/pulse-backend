package controllers

import actions.{AuthAction, AuthHelper}
import models.{Role, RankedAward}
import play.api.Logging
import play.api.libs.json._
import play.api.mvc._
import services.{AwardDefinitionService, AwardRecommendationService, NominationService}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AwardController @Inject()(
                                 cc:                         ControllerComponents,
                                 authAction:                 AuthAction,
                                 awardDefinitionService:     AwardDefinitionService,
                                 awardRecommendationService: AwardRecommendationService,
                                 nominationService:          NominationService
                               )(implicit ec: ExecutionContext) extends AbstractController(cc) with Logging {

  private val managerRoles: Set[Role] = Set(
    Role.fromString("SUPER_ADMIN"),
    Role.fromString("ADMIN"),
    Role.fromString("HR"),
    Role.fromString("DEPARTMENT_MANAGER"),
    Role.fromString("TEAM_LEAD")
  )

  // POST /awards/definitions
  def createAwardDefinition: Action[JsValue] = authAction.async(parse.json) { request =>
    AuthHelper.authorize[JsValue](managerRoles) { req =>
      req.user.companyId match {
        case Some(companyId) =>
          val body         = req.body
          val departmentId = (body \ "departmentId").asOpt[String]
          val name         = (body \ "name").asOpt[String]
          val description  = (body \ "description").asOpt[String]
          val criteriaText = (body \ "criteriaText").asOpt[String]

          (name, description, criteriaText) match {
            case (Some(n), Some(d), Some(c)) =>
              awardDefinitionService.createAwardDefinition(
                companyId    = companyId,
                departmentId = departmentId,
                name         = n,
                description  = d,
                criteriaText = c,
                createdBy    = req.user.userId
              ).map {
                case Right(award) =>
                  Created(Json.obj(
                    "id"           -> award.id,
                    "name"         -> award.name,
                    "companyId"    -> award.companyId,
                    "departmentId" -> award.departmentId,
                    "description"  -> award.description,
                    "criteriaText" -> award.criteriaText
                  ))
                case Left(err) =>
                  logger.error(s"[AwardController] createAwardDefinition failed: $err")
                  InternalServerError(Json.obj("error" -> err))
              }

            case _ =>
              Future.successful(BadRequest(Json.obj(
                "error" -> "Missing required fields: name, description, criteriaText"
              )))
          }

        case None =>
          Future.successful(Forbidden(Json.obj("error" -> "User must belong to a company to create awards")))
      }
    }(ec)(request)
  }

  // GET /awards/definitions?departmentId=...
  def listAwardDefinitions: Action[AnyContent] = authAction.async { request =>
    AuthHelper.authorize[AnyContent](managerRoles) { req =>
      req.user.companyId match {
        case Some(companyId) =>
          val departmentId = req.queryString.get("departmentId").flatMap(_.headOption)

          val serviceFuture = departmentId match {
            case Some(deptId) =>
              awardDefinitionService.listByCompanyAndDepartment(companyId, deptId)
            case None =>
              awardDefinitionService.listByCompany(companyId)
          }

          serviceFuture.map {
            case Right(awards) => Ok(Json.toJson(awards))
            case Left(err)     => InternalServerError(Json.obj("error" -> err))
          }

        case None =>
          Future.successful(Forbidden(Json.obj("error" -> "User must belong to a company to list awards")))
      }
    }(ec)(request)
  }

  // GET /awards/definitions/:id
  def getAwardDefinition(id: String): Action[AnyContent] = authAction.async { request =>
    AuthHelper.authorize[AnyContent](managerRoles) { _ =>
      awardDefinitionService.getById(id).map {
        case Right(award) => Ok(Json.toJson(award))
        case Left(err)    => NotFound(Json.obj("error" -> err))
      }
    }(ec)(request)
  }

  // PUT /awards/definitions/:id
  def updateAwardDefinition(id: String): Action[JsValue] = authAction.async(parse.json) { request =>
    AuthHelper.authorize[JsValue](managerRoles) { req =>
      val body         = req.body
      val name         = (body \ "name").asOpt[String]
      val description  = (body \ "description").asOpt[String]
      val criteriaText = (body \ "criteriaText").asOpt[String]
      val departmentId = (body \ "departmentId").asOpt[String]

      (name, description, criteriaText) match {
        case (Some(n), Some(d), Some(c)) =>
          awardDefinitionService.updateAwardDefinition(
            id           = id,
            name         = n,
            description  = d,
            criteriaText = c,
            departmentId = departmentId,
            requesterId  = req.user.userId
          ).map {
            case Right(award) => Ok(Json.toJson(award))
            case Left(err)    => InternalServerError(Json.obj("error" -> err))
          }
        case _ =>
          Future.successful(BadRequest(Json.obj(
            "error" -> "Missing required fields: name, description, criteriaText"
          )))
      }
    }(ec)(request)
  }

  // DELETE /awards/definitions/:id
  def deleteAwardDefinition(id: String): Action[AnyContent] = authAction.async { request =>
    AuthHelper.authorize[AnyContent](managerRoles) { _ =>
      awardDefinitionService.deleteAwardDefinition(id).map {
        case Right(_)  => NoContent
        case Left(err) => NotFound(Json.obj("error" -> err))
      }
    }(ec)(request)
  }

  // =========================================================================
  // Award Recommendation endpoints
  // =========================================================================

  // POST /awards/recommendations
  // Returns top-3 ranked award recommendations for the nominee, or a clear
  // "insufficient match" message if no award scores above 0.55.
  def recommendAwards: Action[JsValue] = authAction.async(parse.json) { request =>
    AuthHelper.authorize[JsValue](managerRoles) { req =>
      req.user.companyId match {
        case Some(companyId) =>
          val body         = req.body
          val nomineeId    = (body \ "nomineeId").asOpt[String]
          val departmentId = (body \ "departmentId").asOpt[String]

          (nomineeId, departmentId) match {
            case (Some(nid), Some(did)) =>
              awardRecommendationService.recommendAwards(
                nomineeId    = nid,
                requestedBy  = req.user.userId,
                companyId    = companyId,
                departmentId = did
              ).map {
                case Right(result) =>
                  Ok(Json.obj(
                    "nomineeId"      -> result.nomineeId,
                    "profileSummary" -> result.profileSummary,
                    "recommendations" -> Json.toJson(result.rankedAwards)
                  ))
                case Left(err) =>
                  // Distinguish between data-insufficiency and system errors
                  if (err.startsWith("Insufficient data"))
                    UnprocessableEntity(Json.obj("error" -> err))
                  else if (err.startsWith("No awards matched"))
                    Ok(Json.obj(
                      "nomineeId"      -> nid,
                      "insufficientMatch" -> true,
                      "message"        -> err
                    ))
                  else {
                    logger.error(s"[AwardController] recommendAwards failed: $err")
                    InternalServerError(Json.obj("error" -> err))
                  }
              }

            case _ =>
              Future.successful(BadRequest(Json.obj(
                "error" -> "Missing required fields: nomineeId, departmentId"
              )))
          }

        case None =>
          Future.successful(Forbidden(Json.obj("error" -> "User must belong to a company to get recommendations")))
      }
    }(ec)(request)
  }

  // POST /awards/nominations/draft
  //
  // Manager has chosen an award — generate the nomination draft.
  // Delegates entirely to the existing NominationService.generateDraft().
  //
  // Body: {
  //   "nomineeId": "...",
  //   "awardCategory": "...",
  //   "nomineeName": "...",
  //   "nomineeDepartment": "..."
  // }
  def generateNominationDraft: Action[JsValue] = authAction.async(parse.json) { request =>
    AuthHelper.authorize[JsValue](managerRoles) { req =>
      val body              = req.body
      val nomineeId         = (body \ "nomineeId").asOpt[String]
      val awardCategory     = (body \ "awardCategory").asOpt[String]
      val nomineeName       = (body \ "nomineeName").asOpt[String]
      val nomineeDepartment = (body \ "nomineeDepartment").asOpt[String]

      (nomineeId, awardCategory, nomineeName, nomineeDepartment) match {
        case (Some(nid), Some(cat), Some(name), Some(dept)) =>
          nominationService.generateDraft(
            nomineeId        = nid,
            requestedBy      = req.user.userId,
            awardCategory    = cat,
            nomineeName      = name,
            nomineeDepartment = dept
          ).map {
            case Right(draft) =>
              Created(Json.obj(
                "draftId"      -> draft.id,
                "awardCategory" -> draft.awardCategory,
                "draftText"    -> draft.draftText,
                "skillsCited"  -> draft.skillsCited
              ))
            case Left(err) =>
              logger.error(s"[AwardController] generateNominationDraft failed: $err")
              InternalServerError(Json.obj("error" -> err))
          }

        case _ =>
          Future.successful(BadRequest(Json.obj(
            "error" -> "Missing required fields: nomineeId, awardCategory, nomineeName, nomineeDepartment"
          )))
      }
    }(ec)(request)
  }

  // GET /awards/recommendations/history/:nomineeId
  def getRecommendationHistory(nomineeId: String): Action[AnyContent] = authAction.async { request =>
    AuthHelper.authorize[AnyContent](managerRoles) { _ =>
      awardRecommendationService.getRecommendationHistory(nomineeId).map { recs =>
        Ok(Json.toJson(recs))
      }
    }(ec)(request)
  }
}