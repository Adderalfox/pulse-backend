package repositories

import javax.inject._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}
import models.User
import models.Role

import java.util.UUID

@Singleton
class UserRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig.profile.api._

  implicit val roleColumnType = MappedColumnType.base[Role, String](
    _.name,
    Role.fromString
  )

  private class Users(tag: Tag) extends Table[User](tag, "users") {
    def id = column[String]("id", O.PrimaryKey)

    def name = column[String]("name")

    def email = column[String]("email")

    def password = column[String]("password")

    def role = column[Role]("role")

    def totalPoints = column[Int]("total_points")

    def companyId = column[Option[String]]("company_id")

    def departmentId = column[Option[String]]("department_id")

    def * = (id, name, email, password, role, totalPoints, companyId, departmentId) <> ((User.apply _).tupled, User.unapply)
  }

  private val users = TableQuery[Users]

  def create(name: String, email: String, password: String, role: Role, companyId: Option[String], departmentId: Option[String]): Future[User] = {
    val newId = UUID.randomUUID().toString

    val newUser = User(newId, name, email, password, role, 0, companyId, departmentId)

    dbConfig.db.run(users += newUser).map(_ => newUser)
  }

  def findByEmail(email: String): Future[Option[User]] = {
    dbConfig.db.run(users.filter(_.email === email).result.headOption)
  }

  def findById(userId: String): Future[Option[User]] = {
    dbConfig.db.run(users.filter(_.id === userId).result.headOption)
  }
}