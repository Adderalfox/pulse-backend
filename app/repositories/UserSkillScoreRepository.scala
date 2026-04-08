package repositories

import javax.inject._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import models.UserSkillScore

import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserSkillScoreRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig.profile.api._

  private class UserSkillScoreTable(tag: Tag) extends Table[UserSkillScore](tag, "user_skill_scores") {
    def id = column[String]("id", O.PrimaryKey)
    def userId = column[String]("user_id")
    def companyId = column[String]("company_id")
    def skillName = column[String]("skill_name")
    def score = column[Double]("score")
    def endorsementCount = column[Int]("endorsement_count")
    def certified = column[Boolean]("certified")
    def updatedAt = column[Option[java.time.LocalDateTime]]("updated_at")

    def * = (id, userId, companyId, skillName, score, endorsementCount, certified, updatedAt) <> ((UserSkillScore.apply _).tupled, UserSkillScore.unapply)
  }

  private val userSkillScores = TableQuery[UserSkillScoreTable]

  def upsert(scoreEntry: UserSkillScore): Future[Unit] = {

    val query =
      sqlu"""
            INSERT INTO user_skill_scores
            (id, user_id, company_id, skill_name, score, endorsement_count, certified)
            VALUES (${scoreEntry.id}, ${scoreEntry.userId}, ${scoreEntry.companyId}, ${scoreEntry.skillName}, ${scoreEntry.score}, ${scoreEntry.endorsementCount}, ${scoreEntry.certified})
            ON CONFLICT (user_id, skill_name)
            DO UPDATE SET
            score = user_skill_scores.score + EXCLUDED.score,
            endorsement_count = user_skill_scores.endorsement_count + 1,
            updated_at = NOW()
            """
            dbConfig.db.run(query).map(_ => ())
  }

  def getTopSkillsForUser(userId: String, limit: Int): Future[Seq[UserSkillScore]] = {
    dbConfig.db.run(
      userSkillScores
        .filter(_.userId === userId)
        .sortBy(_.score.desc)
        .take(limit)
        .result
    )
  }

  def getUserWithSkill(skillName: String, companyId: String): Future[Seq[UserSkillScore]] = {
    dbConfig.db.run(
      userSkillScores
        .filter(s => s.skillName === skillName && s.companyId === companyId)
        .sortBy(_.score.desc)
        .result
    )
  }
}