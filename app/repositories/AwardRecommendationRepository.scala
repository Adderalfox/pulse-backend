package repositories

import models.AwardRecommendation
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile.api._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AwardRecommendationRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[slick.jdbc.JdbcProfile]
  import dbConfig._

  private class AwardRecommendationsTable(tag: Tag)
    extends Table[AwardRecommendation](tag, "award_recommendations") {

    def id                = column[String]("id", O.PrimaryKey)
    def nomineeId         = column[String]("nominee_id")
    def requestedBy       = column[String]("requested_by")
    def recommendedAwards = column[String]("recommended_awards")
    def profileSummary    = column[String]("profile_summary")

    def * = (id, nomineeId, requestedBy, recommendedAwards, profileSummary)
      .mapTo[AwardRecommendation]
  }

  private val table = TableQuery[AwardRecommendationsTable]

  def insert(rec: AwardRecommendation): Future[AwardRecommendation] =
    db.run(table += rec).map(_ => rec)

  def findById(id: String): Future[Option[AwardRecommendation]] =
    db.run(table.filter(_.id === id).result.headOption)

  def findByNominee(nomineeId: String): Future[Seq[AwardRecommendation]] =
    db.run(table.filter(_.nomineeId === nomineeId).result)

  def findByRequester(requestedBy: String): Future[Seq[AwardRecommendation]] =
    db.run(table.filter(_.requestedBy === requestedBy).result)
}