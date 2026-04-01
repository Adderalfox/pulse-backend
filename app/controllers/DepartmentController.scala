package controllers

import actions.{AuthAction, AuthHelper}

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import services.DepartmentService
import models.Role

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DepartmentController @Inject()(cc: ControllerComponents, departmentService: DepartmentService, authAction: AuthAction)(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  def createDepartment = authAction.async(parse.json) { request =>

    // Look at companyId if we can set it automatically instead of sending it in body by user
    AuthHelper.authorize[JsValue](
      Set("SUPER_ADMIN", "ADMIN").map(Role.fromString)
    ) { req =>
      val name = (req.body \ "name").as[String]
      val companyId = (req.body \ "companyId").asOpt[Long]

      try {
        departmentService.createDepartment(
          requester = req.user,
          name = name,
          companyId = companyId
        ).map {
          case Right(department) =>
            Ok(Json.obj("departmentId" -> department.id))
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