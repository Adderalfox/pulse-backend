package services

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import repositories.UserRepository
import utils.{JwtUtil, PasswordUtil}
import models.{User, JwtPayload, Role}
import org.postgresql.util.PSQLException
import security.RolePermissions

@Singleton
class AuthService @Inject()(userRepo: UserRepository)(implicit ec: ExecutionContext) {

  def register(requester: JwtPayload, name: String, email: String, password: String, role: String, companyId: Option[Long], departmentId: Option[Long]): Future[Either[String, User]] = {
    if (!RolePermissions.canCreate(requester.role, role)) {
      return Future.successful(Left("You are not allowed to create this role"))
    }

    if (requester.companyId != companyId) {
      return Future.successful(Left("Cannot create user in another company"))
    }

    userRepo.findByEmail(email).flatMap {
      case Some(_) =>
        Future.successful(Left("User already exists"))

      case None =>
        val hashed = PasswordUtil.hash(password)
        userRepo.create(name, email, hashed, role, companyId, departmentId)
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
        val payload = JwtPayload(
          userId = user.id,
          email = user.email,
          role = user.role,
          companyId = user.companyId,
          departmentId = user.departmentId
        )

        Some(JwtUtil.generateToken(payload))
      case _ => None
    }
  }
}