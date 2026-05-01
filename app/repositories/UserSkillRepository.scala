package repositories

import models.UserSkill
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile

import java.sql.Timestamp
import java.time.{OffsetDateTime, ZoneOffset}
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserSkillRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[PostgresProfile]

  import dbConfig.profile.api._

//  implicit val offsetDateTimeMapper: BaseColumnType[OffsetDateTime] =
//    MappedColumnType.base[OffsetDateTime, Timestamp](
//      odt => Timestamp.from(odt.toInstant),
//      ts  => ts.toInstant.atOffset(ZoneOffset.UTC)
//    )

  private class UserSkillTable(tag: Tag) extends Table[UserSkill](tag, "user_skills") {
    def id = column[String]("id", O.PrimaryKey)

    def userId = column[String]("user_id")

    def skillId = column[String]("skill_id")

    def rawScore = column[Double]("raw_score")

    def recencyWeightedScore = column[Double]("recency_weighted_score")

    def frequencyCount = column[Int]("frequency_count")

    def appreciatorDiversity = column[Int]("appreciator_diversity")

    def compositeScore = column[Double]("composite_score")

    def appreciatorIds = column[String]("appreciator_ids")

//    def lastUpdatedAt = column[Option[String]]("last_updated_at")

//    def * = (id, userId, skillId, rawScore, recencyWeightedScore, frequencyCount, appreciatorDiversity, compositeScore, appreciatorIds, lastUpdatedAt) <> ((UserSkill.apply _).tupled, UserSkill.unapply)
    def * = (id, userId, skillId, rawScore, recencyWeightedScore, frequencyCount, appreciatorDiversity, compositeScore, appreciatorIds) <> ((UserSkill.apply _).tupled, UserSkill.unapply)


  }

  private val userSkills = TableQuery[UserSkillTable]

  def findByUserId(userId: String): Future[Seq[UserSkill]] = {
    dbConfig.db.run(userSkills.filter(_.userId === userId).result)
  }

  def findByUserAndSkill(userId: String, skillId: String): Future[Option[UserSkill]] = {
    dbConfig.db.run(
      userSkills
        .filter(r => r.userId === userId && r.skillId === skillId)
        .result.headOption
    )
  }

  def insert(userSkill: UserSkill): Future[UserSkill] = {
    dbConfig.db.run((userSkills += userSkill).map(_ => userSkill))
  }

  def update(userSkill: UserSkill): Future[UserSkill] = {
    dbConfig.db.run(userSkills.filter(_.id === userSkill.id).update(userSkill).map(_ => userSkill))
  }

  def getTopSkillsForUser(userId: String, limit: Int = 10): Future[Seq[UserSkill]] = {
    dbConfig.db.run(userSkills.filter(_.userId === userId).sortBy(_.compositeScore.desc).take(limit).result)
  }

  def getTopUsersForSkill(skillId: String, limit: Int = 10): Future[Seq[UserSkill]] = {
    dbConfig.db.run(userSkills.filter(_.skillId === skillId).sortBy(_.compositeScore.desc).take(limit).result)
  }

  def getCountsByDepartmentUsers(userIds: Seq[String]): Future[Seq[UserSkill]] = {
    dbConfig.db.run(userSkills.filter(_.userId inSet userIds).result)
  }
}