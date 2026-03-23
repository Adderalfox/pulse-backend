package repositories

import javax.inject._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}
import models.Department

import java.time.LocalDateTime

@Singleton
class DepartmentRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig.profile.api._

  private class DepartmentTable(tag: Tag) extends Table[Department](tag, "departments") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def companyId = column[Long]("company_id")
    def createdAt = column[Option[java.time.LocalDateTime]]("created_at")

    def * = (id, name, companyId, createdAt) <> ((Department.apply _).tupled, Department.unapply)
  }

  private val departments = TableQuery[DepartmentTable]

  def create(name: String, companyId: Long): Future[Department] = {
    val now = Some(LocalDateTime.now())

    val insertQuery = (departments returning departments.map(_.id)
      into ((department, newId) => department.copy(id = newId))) +=
      Department(0L, name, companyId, now)

    dbConfig.db.run(insertQuery)
  }

  def findById(id: Long): Future[Option[Department]] =
    dbConfig.db.run(departments.filter(_.id === id).result.headOption)
}