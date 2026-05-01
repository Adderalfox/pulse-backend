package services

import models.SkillCategory
import models.insights._
import repositories.{SkillRepository, UserRepository, UserSkillRepository}
import play.api.Logging

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class InsightService @Inject()(userSkillRepo: UserSkillRepository, skillRepo: SkillRepository)(implicit ec: ExecutionContext) extends Logging {
  def getUnderrated(userIds: Seq[String], appreciationCountByUser: Map[String, Int], userNames: Map[String, String], userDepts: Map[String, String]): Future[List[UnderratedEmployee]] =
    Future.sequence(
      userIds.map { userId =>
        userSkillRepo.getTopSkillsForUser(userId, 5).map { topUserSkills =>
          val received = appreciationCountByUser.getOrElse(userId, 0)
          val skillCount = topUserSkills.size
          val avgDiversity = if (topUserSkills.isEmpty) 0.0
          else topUserSkills.map(_.appreciatorDiversity).sum.toDouble / topUserSkills.size
          val workSignal = skillCount * 0.5 + avgDiversity * 0.5
          val gapScore = workSignal / (received + 1.0)
          (userId, workSignal, gapScore, topUserSkills)
        }
      }
    ).map { results =>
      val sorted = results.sortBy(-_._3)
      val threshold = if (sorted.size > 4) sorted(sorted.size / 4)._3 else 0.0
      sorted.filter(_._3 >= threshold).take(20).map { case (userId, workSignal, gapScore, topSkills) =>
      UnderratedEmployee(
        userId = userId,
        displayName = userNames.getOrElse(userId, userId),
        department = userDepts.getOrElse(userId, "Unknown"),
        topSkills = topSkills.map(_.skillId).toList,
        appreciationReceivedCount = appreciationCountByUser.getOrElse(userId, 0),
        workSignalScore = workSignal,
        recognitionGapScore = gapScore
      )
      }.toList
    }

  def computeImbalance(teamId: String, teamName: String, countsPerUser: List[Int]): RecognitionImbalanceReport = {
    val n = countsPerUser.size
    val total = countsPerUser.sum.toDouble
    if (n < 2 || total == 0) return RecognitionImbalanceReport(teamId, teamName, 0.0, 0.0, 0, 0, "INSUFFICIENT_DATA")

    val sorted = countsPerUser.sorted.toVector
    val gini = {
      val numerator = (1 to n).zip(sorted).map { case (i, x) => i.toDouble * x }.sum
      (2.0 * numerator) / (n * total) - (n + 1.0) / n
    }
    val top20Count = Math.max(1, (n * 0.2).ceil.toInt)
    val top20Share = sorted.reverse.take(top20Count).sum.toDouble / total
    val tag = if (gini > 0.5) "HIGH_IMBALANCE" else if (gini > 0.3) "MODERATE" else "HEALTHY"

    RecognitionImbalanceReport(
      teamId = teamId,
      teamName = teamName,
      giniCoefficient = Math.round(gini * 1000).toDouble / 1000,
      top20PercentShare = Math.round(top20Share * 1000).toDouble / 1000,
      highReceiversCount = top20Count,
      lowReceiversCount = n - top20Count,
      tag = tag
    )
  }

  def computeTrends(currentPeriod: Map[String, Int], previousPeriod: Map[String, Int], skillCategories: Map[String, String]): List[SkillTrend] = {
    val allSkills = (currentPeriod.keySet ++ previousPeriod.keySet).toList
    allSkills.map { skillName =>
      val current = currentPeriod.getOrElse(skillName, 0)
      val previous = previousPeriod.getOrElse(skillName, 0)
      val change = (current - previous).toDouble / (previous + 1) * 100
      val trend = if (change > 20) TrendDirection.RISING
      else if (change < -20) TrendDirection.FALLING
      else TrendDirection.STABLE

      SkillTrend(
        skillName = skillName,
        category = skillCategories.getOrElse(skillName, "UNKNOWN"),
        currentPeriodCount = current,
        previousPeriodCount = previous,
        changePercent = Math.round(change * 10).toDouble / 10,
        trend = trend
      )
    }.sortBy(t => -Math.abs(t.changePercent))
  }

  def getTopExperts(skillNormalizedName: String, userNames: Map[String, String], limit: Int = 10): Future[Either[String, List[ExpertResult]]] = {
    skillRepo.findByNormalizedName(skillNormalizedName).flatMap {
      case None =>
        Future.successful(Left(s"Skill '$skillNormalizedName' not found"))
      case Some(skill) =>
        userSkillRepo.getTopSkillsForUser(skill.id, limit).map { userSkills =>
          Right(userSkills.map { us =>
            ExpertResult(
              userId = us.userId,
              displayName = userNames.getOrElse(us.userId, us.userId),
              compositeScore = us.compositeScore,
              frequencyCount = us.frequencyCount,
              appreciatorDiversity = us.appreciatorDiversity,
              explanation = s"Recognized ${us.frequencyCount} times by ${us.appreciatorDiversity} unique colleagues."
            )
          }.toList)
        }
    }
  }
}