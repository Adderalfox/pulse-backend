package services

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import repositories.CompanyRepository
import models.{JwtPayload, Company, Role}
import org.postgresql.util.PSQLException
import security.RolePermissions

@Singleton
class CompanyService @Inject()(companyRepo: CompanyRepository)(implicit ec: ExecutionContext) {

  def createCompany(requester: JwtPayload, name: String, domain: String): Future[Either[String, Company]] = {
    if (!RolePermissions.hasAccess(requester.role, Set(Role.SUPER_ADMIN))) {
      return Future.successful(Left("Access Denied!"))
    }

    companyRepo.findByDomain(domain).flatMap {
      case Some(_) =>
        Future.successful(Left("Company already exists"))

      case None =>
        companyRepo.create(name, domain)
          .map(company => Right(company))
          .recover{
            case ex: PSQLException if ex.getMessage.contains("duplicate") =>
              Left("Company already exists")
          }
    }
  }
}