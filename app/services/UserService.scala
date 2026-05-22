package services

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import repositories.UserRepository
import models.{JwtPayload, User, Role}
import security.RolePermissions

@Singleton
class UserService @Inject()(userRepo: UserRepository)(implicit ec: ExecutionContext) {
  def listUsers(requester: JwtPayload): Future[Either[String, Seq[User]]] = {
    val allowedRoles: Set[Role] = Set(
      Role.SUPER_ADMIN,
      Role.ADMIN,
      Role.HR,
      Role.DEPARTMENT_MANAGER,
      Role.TEAM_LEAD,
      Role.EMPLOYEE
    )

    if (!RolePermissions.hasAccess(requester.role, allowedRoles)) {
      return Future.successful(Left("Access Denied!"))
    }

    val companyScope = requester.role match {
      case Role.SUPER_ADMIN => None
      case _ => requester.companyId
    }

    if (requester.role == Role.SUPER_ADMIN) {
      userRepo.listUsers(None).map(Right(_))
    } else {
      companyScope match {
        case Some(companyId) =>
          userRepo.listUsers(Some(companyId)).map { users =>
            val visibleUsers = if (requester.role == Role.EMPLOYEE) {
              users.filterNot(_.role == Role.SUPER_ADMIN)
            } else {
              users
            }

            Right(visibleUsers)
          }
        case None =>
          Future.successful(Left("Missing company scope for requester"))
      }
    }
  }
}