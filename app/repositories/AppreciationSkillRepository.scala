package repositories

import models.AppreciationSkill
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile

import java.time.LocalDateTime
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class AppreciationSkillRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  private val dbConfig = dbConfigProvider.get[PostgresProfile]

  import dbConfig.profile.api._

  private class AppreciationSkillTable(tag: Tag) extends Table[AppreciationSkill](tag, "appreciation_skills") {
    def id = column[String]("id", O.PrimaryKey)
    def appreciationId = column[String]("appreciation_id")
    def skillId = column[String]("skill_id")
    def llmConfidence = column[Double]("llm_confidence")
    def extractionModel = column[String]("extraction_model")
    def extractedAt = column[Option[LocalDateTime]]("extracted_at")

    def * = (id, appreciationId, skillId, llmConfidence,extractionModel, extractedAt) <> ((AppreciationSkill.apply _).tupled, AppreciationSkill.unapply)
  }

  private val appreciationSkills = TableQuery[AppreciationSkillTable]

  def insertAll(rows: Seq[AppreciationSkill]): Future[Unit] =
    dbConfig.db.run((appreciationSkills ++= rows).map(_ => ()))

  def findByAppreciationId(appreciationId: String): Future[Seq[AppreciationSkill]] = {
    dbConfig.db.run(appreciationSkills.filter(_.appreciationId === appreciationId).result)
  }

  def hasBeenExtracted(appreciationId: String): Future[Boolean] = {
    dbConfig.db.run(appreciationSkills.filter(_.appreciationId === appreciationId).exists.result)
  }

  def findUnextractedId(allAppreciationIds: Seq[String]): Future[Seq[String]] = {
    dbConfig.db.run(
      appreciationSkills.filter(_.appreciationId inSet allAppreciationIds).map(_.appreciationId).distinct.result
    ).map { extracted =>
      allAppreciationIds.filterNot(extracted.toSet.contains)
    }
  }
}