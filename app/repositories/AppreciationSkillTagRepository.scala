package repositories

import javax.inject._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile
import models.AppreciationSkillTag

import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AppreciationSkillTagRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  private val dbConfig = dbConfigProvider.get[PostgresProfile]

  import dbConfig.profile.api._

  private class AppreciationSkillTagTable(tag: Tag) extends Table[AppreciationSkillTag](tag, "appreciation_skill_tags") {
    def id = column[String]("id", O.PrimaryKey)

    def appreciationId = column[String]("appreciation_id")

    def skillName = column[String]("skill_name")

    def confidenceScore = column[Double]("confidence_score")

    def source = column[String]("source")

    def createdAt = column[Option[java.time.LocalDateTime]]("created_at")

    def * = (id, appreciationId, skillName, confidenceScore, source, createdAt) <> ((AppreciationSkillTag.apply _).tupled, AppreciationSkillTag.unapply)
  }

  private val appreciationSkillTags = TableQuery[AppreciationSkillTagTable]

  def create(tag: AppreciationSkillTag): Future[AppreciationSkillTag] = {
    val tagWithId =
      if (tag.id.nonEmpty) tag
      else tag.copy(id = UUID.randomUUID().toString)

    dbConfig.db.run(appreciationSkillTags += tagWithId).map(_ => tagWithId)
  }

  def findById(id: String): Future[Option[AppreciationSkillTag]] =
    dbConfig.db.run(appreciationSkillTags.filter(_.id === id).result.headOption)

  def createMany(tags: Seq[AppreciationSkillTag]): Future[Seq[AppreciationSkillTag]] = {
    val tagWithIds = tags.map { tag =>
      if (tag.id.nonEmpty) tag
      else tag.copy(id = UUID.randomUUID().toString)
    }

    dbConfig.db.run(appreciationSkillTags ++= tagWithIds).map(_ => tagWithIds)
  }

  def getByAppreciation(appreciationId: String): Future[Seq[AppreciationSkillTag]] = {
    dbConfig.db.run(
      appreciationSkillTags
        .filter(_.appreciationId === appreciationId)
        .sortBy(_.createdAt.desc)
        .result
    )
  }
}