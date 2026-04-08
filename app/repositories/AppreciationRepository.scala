package repositories

import javax.inject._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import models.Appreciation

import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AppreciationRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig.profile.api._

  private class AppreciationTable(tag: Tag) extends Table[Appreciation](tag, "appreciations") {
    def id = column[String]("id", O.PrimaryKey)

    def giverId = column[String]("giver_id")

    def receiverId = column[String]("receiver_id")

    def companyId = column[String]("company_id")

    def departmentId = column[Option[String]]("department_id")

    def text = column[String]("text")

    def appreciationType = column[String]("appreciation_type")

    def visibility = column[String]("visibility")

    def pointsAwarded = column[Int]("points_awarded")

    def createdAt = column[Option[java.time.LocalDateTime]]("created_at")

    def updatedAt = column[Option[java.time.LocalDateTime]]("updated_at")

    def * = (id, giverId, receiverId, companyId, departmentId, text, appreciationType, visibility, pointsAwarded, createdAt, updatedAt) <> ((Appreciation.apply _).tupled, Appreciation.unapply)
  }

  private val appreciations = TableQuery[AppreciationTable]

  def create(app: Appreciation): Future[Appreciation] = {
    val appWithId =
      if (app.id.nonEmpty) app
      else app.copy(id = UUID.randomUUID().toString)

    dbConfig.db.run(appreciations += appWithId).map(_ => appWithId)
  }

  def findById(id: String): Future[Option[Appreciation]] =
    dbConfig.db.run(appreciations.filter(_.id === id).result.headOption)

  def getByReceiver(receiverId: String): Future[Seq[Appreciation]] =
    dbConfig.db.run(
      appreciations
        .filter(_.receiverId === receiverId)
        .sortBy(_.createdAt.desc)
        .result
    )

  def getByGiver(giverId: String): Future[Seq[Appreciation]] =
    dbConfig.db.run(
      appreciations
        .filter(_.giverId === giverId)
        .sortBy(_.createdAt.desc)
        .result
    )

  def getRawFeedCandidates(companyId: String, limit: Int, offset: Int): Future[Seq[Appreciation]] = {
    dbConfig.db.run(
      appreciations
        .filter(_.companyId === companyId)
        .sortBy(_.createdAt.desc)
        .drop(offset)
        .take(limit)
        .result
    )
  }
}