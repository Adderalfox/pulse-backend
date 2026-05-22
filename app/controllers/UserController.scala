package controllers

import javax.inject._
import scala.concurrent.{Future, ExecutionContext}
import play.api.libs.json._
import play.api.mvc._
import actions.AuthAction
import services.UserService

@Singleton
class UserController @Inject()(cc: ControllerComponents, userService: UserService, authAction: AuthAction)(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  def listUsers = authAction.async { request =>
    userService.listUsers(request.user).map {
      case Right(users) =>
        Ok(
          Json.obj(
            "users" -> JsArray(
              users.map { user =>
                Json.obj(
                  "id" -> user.id,
                  "name" -> user.name,
                  "email" -> user.email,
                  "role" -> user.role.name,
                  "totalPoints" -> user.totalPoints,
                  "companyId" -> user.companyId,
                  "departmentId" -> user.departmentId
                )
              }
            )
          )
        )
      case Left(error) =>
        Forbidden(Json.obj("error" -> error))
    }.recover {
      case ex: Throwable =>
        InternalServerError(Json.obj("error" -> s"Failed to list users: ${ex.getMessage}"))
    }
  }
}