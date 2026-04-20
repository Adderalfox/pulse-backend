package repositories

import javax.inject._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile

import scala.concurrent.{ExecutionContext, Future}
import models.Department

import java.time.LocalDateTime
import java.util.UUID

@Singleton
class DepartmentRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[PostgresProfile]

  import dbConfig.profile.api._

  private class DepartmentTable(tag: Tag) extends Table[Department](tag, "departments") {
    def id = column[String]("id", O.PrimaryKey)
    def name = column[String]("name")
    def companyId = column[Option[String]]("company_id")
    def createdAt = column[Option[java.time.LocalDateTime]]("created_at")

    def * = (id, name, companyId, createdAt) <> ((Department.apply _).tupled, Department.unapply)
  }

  private val departments = TableQuery[DepartmentTable]

  def create(name: String, companyId: Option[String]): Future[Department] = {
    val now = Some(LocalDateTime.now())
    val newId = UUID.randomUUID().toString

    val newDepartment = Department(newId, name, companyId, now)

    dbConfig.db.run(departments += newDepartment).map(_ => newDepartment)
  }

  def findByNameAndCompany(name: String, companyId: Option[String]): Future[Option[Department]] = {
    dbConfig.db.run(departments.filter(d => d.name === name && d.companyId === companyId)
    .result
    .headOption)
  }
}