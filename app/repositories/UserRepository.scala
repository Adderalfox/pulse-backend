package repositories

import javax.inject._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import scala.concurrent.{ExecutionContext, Future}
import models.User

@Singleton
class UserRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig.profile.api._

  class Users(tag: Tag) extends Table[User](tag, "users") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def email = column[String]("email")
    def password = column[String]("password")
    def role = column[String]("role")
    def totalPoints = column[Int]("total_points")

    def * = (id, name, email, password, role, totalPoints) <> ((User.apply _).tupled, User.unapply)
  }

  val users = TableQuery[Users]

  def create(name: String, email: String, password: String, role: String): Future[User] = {
    val insertQuery = (users returning users.map(_.id)
      into ((user, newId) => user.copy(id = newId))) +=
      User(0L, name, email, password, role, 0)

    dbConfig.db.run(insertQuery)
  }

  def findByEmail(email: String): Future[Option[User]] = {
    dbConfig.db.run(users.filter(_.email === email).result.headOption)
  }
}