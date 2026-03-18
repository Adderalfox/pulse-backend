package services

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import repositories.UserRepository
import utils.{PasswordUtil, JwtUtil}
import models.User

@Singleton
class AuthService @Inject()(userRepo: UserRepository)(implicit ec: ExecutionContext) {

  def register(name: String, email: String, password: String, role: String): Future[User] = {
    val hashed = PasswordUtil.hash(password)
    userRepo.create(name, email, hashed, role)
  }

  def login(email: String, password: String): Future[Option[String]] = {
    userRepo.findByEmail(email).map {
      case Some(user) if PasswordUtil.check(password, user.password) =>
        Some(JwtUtil.generateToken(user.id))
      case _ => None
    }
  }
}