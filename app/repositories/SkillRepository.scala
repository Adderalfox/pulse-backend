package repositories

import models.{ Skill, SkillCategory }
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile

import java.time.LocalDateTime
import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class SkillRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  private val dbConfig = dbConfigProvider.get[PostgresProfile]

  import dbConfig.profile.api._

  private implicit val categoryMapper = MappedColumnType.base[SkillCategory, String](
    e => e.name,
    s => SkillCategory.fromString(s)
  )

  private class SkillTable(tag: Tag) extends Table[Skill](tag, "skills") {
    def id = column[String]("id", O.PrimaryKey)
    def name = column[String]("name")
    def normalizedName = column[String]("normalized_name")
    def category = column[SkillCategory]("category")
    def createdAt = column[Option[LocalDateTime]]("created_at")

    def * = (id, name, normalizedName, category, createdAt) <> ((Skill.apply _).tupled, Skill.unapply)
  }

  private val skills = TableQuery[SkillTable]

  def findById(id: String): Future[Option[Skill]] = {
    dbConfig.db.run(skills.filter(_.id === id).result.headOption)
  }

  def findByNormalizedName(normalizedName: String): Future[Option[Skill]] = {
    dbConfig.db.run(skills.filter(_.normalizedName === normalizedName).result.headOption)
  }

  def insert(skill: Skill): Future[Skill] = {
    dbConfig.db.run((skills += skill).map(_ => skill))
  }

  def findAll(): Future[Seq[Skill]] = {
    dbConfig.db.run(skills.result)
  }

  def findByCategory(category: SkillCategory): Future[Seq[Skill]] = {
    dbConfig.db.run(skills.filter(_.category === category).result)
  }

  def searchByName(partial: String): Future[Seq[Skill]] = {
    dbConfig.db.run(skills.filter(_.normalizedName like s"%${partial.toLowerCase}%").result)
  }
}