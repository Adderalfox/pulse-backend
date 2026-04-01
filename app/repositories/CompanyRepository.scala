package repositories

import javax.inject._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import models.Company

import java.util.UUID

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CompanyRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig.profile.api._

//  class Users(tag: Tag) extends Table[User](tag, "users")
  private class CompanyTable(tag: Tag) extends Table[Company](tag, "companies") {
    def id = column[String]("id", O.PrimaryKey)
    def name = column[String]("name")
    def domain = column[String]("domain")
    def createdAt = column[Option[java.time.LocalDateTime]]("created_at")

    def * = (id, name, domain, createdAt) <> ((Company.apply _).tupled, Company.unapply)
  }

  private val companies = TableQuery[CompanyTable]

  def create(name: String, domain: String): Future[Company] = {
    val now = Some(LocalDateTime.now())
    val newId = UUID.randomUUID().toString

    val newCompany = Company(newId, name, domain, now)

    dbConfig.db.run(companies += newCompany).map(_ => newCompany)
  }

  def findByDomain(domain: String): Future[Option[Company]] =
    dbConfig.db.run(companies.filter(_.domain === domain).result.headOption)
}