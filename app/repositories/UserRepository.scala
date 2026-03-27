package repositories

import javax.inject._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import scala.concurrent.{ExecutionContext, Future}
import models.User
import models.Role

@Singleton
class UserRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig.profile.api._

  implicit val roleColumnType = MappedColumnType.base[Role, String](
    _.name,
    Role.fromString
  )

  private class Users(tag: Tag) extends Table[User](tag, "users") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def name = column[String]("name")

    def email = column[String]("email")

    def password = column[String]("password")

    def role = column[Role]("role")

    def totalPoints = column[Int]("total_points")

    def companyId = column[Option[Long]]("company_id")

    def departmentId = column[Option[Long]]("department_id")

    def * = (id, name, email, password, role, totalPoints, companyId, departmentId) <> ((User.apply _).tupled, User.unapply)
  }

  private val users = TableQuery[Users]

  def create(name: String, email: String, password: String, role: Role, companyId: Option[Long], departmentId: Option[Long]): Future[User] = {
    val insertQuery = (users returning users.map(_.id)
      into ((user, newId) => user.copy(id = newId))) +=
      User(0L, name, email, password, role, 0, companyId, departmentId)

    dbConfig.db.run(insertQuery)
  }

  def findByEmail(email: String): Future[Option[User]] = {
    dbConfig.db.run(users.filter(_.email === email).result.headOption)
  }

  // For creation of Super Admin
  def count(): Future[Int] = {
    dbConfig.db.run(users.length.result)
  }
}