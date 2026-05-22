package services

import models.UserSkill
import repositories.UserSkillRepository
import play.api.Logging

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import scala.math.exp

@Singleton
class SkillScoringService @Inject()(userSkillRepo: UserSkillRepository)(implicit ec: ExecutionContext) extends Logging {
  private val lambda = 0.05

  def updateScores(recipientId: String, appreciatorId: String, skills: List[(models.Skill, Double)]): Future[Unit] = {
    Future.sequence(
      skills.map { case (skill, confidence) =>
        updateSingleSkillScore(recipientId, appreciatorId, skill.id, confidence)
      }
    ).map(_ => ())
  }

  private def updateSingleSkillScore(userId: String, appreciatorId: String, skillId: String, llmConfidence: Double): Future[Unit] = {
    userSkillRepo.findByUserAndSkill(userId, skillId).flatMap {
      case None =>
        val newRow = UserSkill(
          id = UUID.randomUUID().toString,
          userId = userId,
          skillId = skillId,
          rawScore = llmConfidence,
          recencyWeightedScore = llmConfidence,
          frequencyCount = 1,
          appreciatorDiversity = 1,
          compositeScore = llmConfidence,
          appreciatorIds = appreciatorId
        )
        userSkillRepo.insert(newRow).map(_ => ())

      case Some(existing) =>
//        val daysSince = existing.lastUpdatedAt
//          .map(s => ChronoUnit.DAYS.between(OffsetDateTime.parse(s), OffsetDateTime.now()).toDouble)
//          .getOrElse(0.0)

        val daysSince = 0.0

        val recencyFactor = exp(-lambda * daysSince)
        val newRecencyWeighted = (existing.recencyWeightedScore * recencyFactor) + llmConfidence
        val newFrequency = existing.frequencyCount + 1

        val existingAppreciators = existing.appreciatorIds.split(",").filter(_.nonEmpty).toSet
        val isNewAppreciator = !existingAppreciators.contains(appreciatorId)
        val newDiversity = existing.appreciatorDiversity + (if (isNewAppreciator) 1 else 0)
        val updatedAppreciatorIds =
          if (isNewAppreciator) existing.appreciatorIds + "," + appreciatorId
          else existing.appreciatorIds

        val maxFrequency = 100.0
        val maxDiversity = 50.0
        val freqNorm = Math.log(1 + newFrequency) / Math.log(1 + maxFrequency)
        val diversityNorm = Math.min(newDiversity.toDouble / maxDiversity, 1.0)
        val recencyNorm = Math.min(newRecencyWeighted / 5.0, 1.0)
        val composite = (0.40 * recencyNorm) + (0.35 * freqNorm) + (0.25 * diversityNorm)

        val updated = existing.copy(
          rawScore = existing.rawScore + llmConfidence,
          recencyWeightedScore = newRecencyWeighted,
          frequencyCount = newFrequency,
          appreciatorDiversity = newDiversity,
          compositeScore = composite,
          appreciatorIds = updatedAppreciatorIds
        )
        userSkillRepo.update(updated).map(_ => ())
    }
  }
}