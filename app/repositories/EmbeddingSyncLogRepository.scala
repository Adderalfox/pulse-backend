package repositories

import models.EmbeddingSyncLog
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile

import java.time.LocalDateTime
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class EmbeddingSyncLogRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  private val dbConfig = dbConfigProvider.get[PostgresProfile]

  import dbConfig.profile.api._

  private class EmbeddingSyncLogTable(tag: Tag) extends Table[EmbeddingSyncLog](tag, "embedding_sync_log") {

    def appreciationId = column[String]("appreciation_id", O.PrimaryKey)
    def qdrantPointId = column[String]("qdrant_point_id")
    def modelVersion = column[String]("model_version")
    def embedded_at = column[Option[LocalDateTime]]("embedded_at")

    def * = (appreciationId, qdrantPointId, modelVersion, embedded_at) <> ((EmbeddingSyncLog.apply _).tupled, EmbeddingSyncLog.unapply)
  }

  private val logs = TableQuery[EmbeddingSyncLogTable]

  def exists(appreciationId: String): Future[Boolean] =
    dbConfig.db.run(logs.filter(_.appreciationId === appreciationId).exists.result)

  def insert(log: EmbeddingSyncLog): Future[Unit] = {
    dbConfig.db.run((logs += log).map(_ => ()))
  }
  def findAll(): Future[Seq[EmbeddingSyncLog]] =
    dbConfig.db.run(logs.result)
}