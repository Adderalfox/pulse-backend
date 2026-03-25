package actions

import javax.inject._
import play.api.mvc._
import utils.JwtUtil
import models.JwtPayload

import scala.concurrent.{ExecutionContext, Future}

class AuthRequest[A](
                      val user: JwtPayload,
                      request: Request[A]
                    ) extends WrappedRequest[A](request)

@Singleton
class AuthAction @Inject()(val parser: BodyParsers.Default)(implicit val executionContext: ExecutionContext)
  extends ActionBuilder[AuthRequest, AnyContent] {
  override def invokeBlock[A](request: Request[A], block: AuthRequest[A] => Future[Result]): Future[Result] = {
    request.headers.get("Authorization") match {
      case Some(tokenHeader) =>
        //        try {
        val token = tokenHeader.replace("Bearer ", "")
        //          val payload = JwtUtil.decodeToken(token)

        JwtUtil.decodeToken(token) match {
          case Some(payload) =>
            val authRequest = new AuthRequest(payload, request)
            block(authRequest)
          case None =>
            Future.successful(Results.Unauthorized("Invalid token"))
        }

      //          val authRequest = new AuthRequest(payload, request)
      //          block(authRequest)
      //        } catch {
      //          case _: Exception =>
      //            Future.successful(Results.Unauthorized("Invalid or expired token"))
      //        }

      case None =>
        Future.successful(Results.Unauthorized("Missing Authorization header"))
    }
  }
}

object AuthHelper {
  def authorize[A](allowedRoles: Set[String])
                  (block: AuthRequest[A] => Future[Result])
                  (implicit ec: ExecutionContext): AuthRequest[A] => Future[Result] = { req =>

    if (allowedRoles.contains(req.user.role)) {
      block(req)
    } else {
      Future.successful(Results.Forbidden("Access denied"))
    }
  }
}