package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import services.AuthService

import scala.concurrent.ExecutionContext

@Singleton
class AuthController @Inject()(cc: ControllerComponents, authService: AuthService)
                              (implicit ec: ExecutionContext)
extends AbstractController(cc) {

  def register = Action.async(parse.json) {  request =>
    val name = (request.body \ "name").as[String]
    val email = (request.body \ "email").as[String]
    val password = (request.body \ "password").as[String]
    val role = (request.body \ "role").as[String]

    authService.register(name, email, password, role).map {
      case Right(user) =>
        Ok(Json.obj("userId" -> user.id))

      case Left(error) =>
        Conflict(Json.obj("error" -> error))
    }
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