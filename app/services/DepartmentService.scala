package services

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import repositories.DepartmentRepository
import models.{Department, JwtPayload, Role}
import org.postgresql.util.PSQLException
import security.RolePermissions

@Singleton
class DepartmentService @Inject()(departmentRepo: DepartmentRepository)(implicit ec: ExecutionContext) {
  def createDepartment(requester: JwtPayload, name: String, companyId: Option[String]): Future[Either[String, Department]] = {

    if (!RolePermissions.hasAccess(requester.role, Set(Role.SUPER_ADMIN, Role.ADMIN))) {
      return Future.successful(Left("Access Denied!"))
    }

    if (requester.role == Role.ADMIN && requester.companyId != companyId) {
      return Future.successful(Left("Access Denied!"))
    }

    departmentRepo.findByNameAndCompany(name, companyId).flatMap {
      case Some(_) =>
        Future.successful(Left("Department already exists"))

      case None =>
        departmentRepo.create(name, companyId)
          .map(department => Right(department))
          .recover {
            case ex: PSQLException if ex.getMessage.contains("duplicate") =>
              Left("Department already exists")
          }
    }
  }
}