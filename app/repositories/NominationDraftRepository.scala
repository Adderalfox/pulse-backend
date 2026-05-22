package repositories

import models.NominationDraft
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import java.time.LocalDateTime
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NominationDraftRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig.profile.api._

  private class NominationDraftTable(tag: Tag) extends Table[NominationDraft](tag, "nomination_drafts") {
    def id = column[String]("id", O.PrimaryKey)
    def nomineeId = column[String]("nominee_id")
    def requestedBy = column[String]("requested_by")
    def awardCategory = column[String]("award_category")
    def draftText = column[String]("draft_text")
    def skillsCited = column[String]("skills_cited")
    def appreciationsUsed = column[Int]("appreciations_used")
    def generatedAt = column[Option[LocalDateTime]]("generated_at")

    def * = (id, nomineeId, requestedBy, awardCategory, draftText, skillsCited, appreciationsUsed, generatedAt) <> ((NominationDraft.apply _).tupled, NominationDraft.unapply)
  }

  private val nominationDrafts = TableQuery[NominationDraftTable]

  def insert(draft: NominationDraft): Future[NominationDraft] =
    dbConfig.db.run((nominationDrafts += draft).map(_ => draft))

  def findByNominee(nomineeId: String): Future[Seq[NominationDraft]] =
    dbConfig.db.run(
      nominationDrafts.filter(_.nomineeId === nomineeId)
        .sortBy(_.generatedAt.desc)
        .result
    )

  def findByRequester(requestedBy: String): Future[Seq[NominationDraft]] =
    dbConfig.db.run(
      nominationDrafts.filter(_.requestedBy === requestedBy)
        .sortBy(_.generatedAt.desc)
        .result
    )
}