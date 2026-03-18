package repositories

import javax.inject._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import scala.concurrent.{ExecutionContext, Future}

@Singleton

class UserRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig.profile.api._

  class Users(tag: Tag) extends Table[(Long, String, String, String, Int)](tag, "users") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name")
    def email = column[String]("email")
    def role = column[String]("role")
    def totalPoints = column[Int]("total_points")

    def * = (id, name, email, role, totalPoints)
  }

  val users = TableQuery[Users]

  def create(name: String, email: String, role: String): Future[Int] =
    dbConfig.db.run(users += (0L, name, email, role, 0))
}