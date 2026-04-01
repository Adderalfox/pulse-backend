package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import services.AuthService
import actions.{AuthAction, AuthHelper}
import models.Role

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthController @Inject()(cc: ControllerComponents, authService: AuthService, authAction: AuthAction)(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  def createUser = authAction.async(parse.json) { request =>

    AuthHelper.authorize[JsValue](
      Set("SUPER_ADMIN", "ADMIN", "HR", "DEPARTMENT_MANAGER", "TEAM_LEAD").map(Role.fromString)
    ) { req =>

      val name = (req.body \ "name").as[String]
      val email = (req.body \ "email").as[String]
      val password = (req.body \ "password").as[String]
      val roleStr = (req.body \ "role").as[String]
      val departmentId = (req.body \ "departmentId").asOpt[String]

      try {
        val role = Role.fromString(roleStr)

        authService.register(
          requester = req.user,
          name = name,
          email = email,
          password = password,
          role = role,
          companyId = req.user.companyId,
          departmentId = departmentId
        ).map {
          case Right(user) =>
            Ok(Json.obj("userId" -> user.id))

          case Left(error) =>
            Forbidden(Json.obj("error" -> error))
        }
      } catch {
        case _: IllegalArgumentException =>
          Future.successful(
            BadRequest(Json.obj("error" -> "Invalid role"))
          )
      }
    }(ec)(request)
  }

  def login = Action.async(parse.json) { request =>
    val email = (request.body \ "email").as[String]
    val password = (request.body \ "password").as[String]

    authService.login(email, password).map {
      case Some(token) => Ok(Json.obj("token" -> token))
      case None => Unauthorized(Json.obj("error" -> "Invalid credentials"))
    }
  }
}