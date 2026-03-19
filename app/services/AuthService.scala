package services

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import repositories.UserRepository
import utils.{JwtUtil, PasswordUtil}
import models.User
import org.postgresql.util.PSQLException

@Singleton
class AuthService @Inject()(userRepo: UserRepository)(implicit ec: ExecutionContext) {

  def register(name: String, email: String, password: String, role: String): Future[Either[String, User]] = {
    userRepo.findByEmail(email).flatMap {
      case Some(_) =>
        Future.successful(Left("User already exists"))

      case None =>
        val hashed = PasswordUtil.hash(password)
        userRepo.create(name, email, hashed, role)
        .map(user => Right(user))
        .recover {
          case ex: PSQLException if ex.getMessage.contains("duplicate") =>
            Left("User already exists")
        }
    }
  }

  def login(email: String, password: String): Future[Option[String]] = {
    userRepo.findByEmail(email).map {
      case Some(user) if PasswordUtil.check(password, user.password) =>
        Some(JwtUtil.generateToken(user.id))
      case _ => None
    }
  }
}