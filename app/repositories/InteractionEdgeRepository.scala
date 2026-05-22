package repositories

import javax.inject._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile
import models.InteractionEdge

import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class InteractionEdgeRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  private val dbConfig = dbConfigProvider.get[PostgresProfile]

  import dbConfig.profile.api._

  private class InteractionEdgeTable(tag: Tag) extends Table[InteractionEdge](tag, "interaction_edges") {
    def id = column[String]("id")

    def userIdFrom = column[String]("user_id_from")

    def userIdTo = column[String]("user_id_to")

    def companyId = column[String]("company_id")

    def interactionType = column[String]("interaction_type")

    def weight = column[Double]("weight")

    def lastSeenAt = column[Option[java.time.LocalDateTime]]("last_seen_at")

    def createdAt = column[Option[java.time.LocalDateTime]]("created_at")

    def * = (id, userIdFrom, userIdTo, companyId, interactionType, weight, lastSeenAt, createdAt) <> ((InteractionEdge.apply _).tupled, InteractionEdge.unapply)
  }

  private val interactionEdges = TableQuery[InteractionEdgeTable]

  def upsert(edge: InteractionEdge): Future[Unit] = {
//    val id = UUID.randomUUID().toString

    val sql =
      sqlu"""
            INSERT INTO interaction_edges
            (id, user_id_from, user_id_to, company_id, interaction_type, weight)
            VALUES (${edge.id}, ${edge.userIdFrom}, ${edge.userIdTo}, ${edge.companyId}, ${edge.interactionType}, ${edge.weight})
            ON CONFLICT (user_id_from, user_id_to, interaction_type)
            DO UPDATE SET
            weight = interaction_edges.weight + 1,
            last_seen_at = NOW()
            """
    dbConfig.db.run(sql).map(_ => ())
  }

  def getEdgesForUser(userId: String): Future[Seq[InteractionEdge]] = {
    dbConfig.db.run(interactionEdges
      .filter(e => e.userIdFrom === userId || e.userIdTo === userId)
      .result)
  }

  def getEdgesBetween(userIdFrom: String, userIdTo: String): Future[Seq[InteractionEdge]] = {
    dbConfig.db.run(
      interactionEdges
        .filter(e => e.userIdFrom === userIdFrom && e.userIdTo === userIdTo)
        .result
    )
  }
}