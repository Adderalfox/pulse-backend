package controllers

import actions.{AuthAction, AuthHelper}

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import services.CompanyService
import models.Role

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CompanyController @Inject()(cc: ControllerComponents, companyService: CompanyService, authAction: AuthAction)(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  def createCompany = authAction.async(parse.json) { request =>

    AuthHelper.authorize[JsValue](
      Set("SUPER_ADMIN").map(Role.fromString)
    ) { req =>

      val name = (req.body \ "name").as[String]
      val domain = (req.body \ "domain").as[String]

      try {
        companyService.createCompany(
          requester = req.user,
          name = name,
          domain = domain
        ).map {
          case Right(company) =>
            Ok(Json.obj("companyId" -> company.id))
          case Left(error) =>
            Forbidden(Json.obj("error" -> error))
        }
      } catch {
        case _: IllegalArgumentException =>
          Future.successful(
            BadRequest(Json.obj("error" -> "Permission Denied"))
          )
      }
    }(ec)(request)
  }
  

}