package services

import models.{Skill, SkillCategory}
import dto.ExtractedSkill
import repositories.SkillRepository

import java.util.UUID
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SkillService @Inject()(skillRepo: SkillRepository)(implicit ec: ExecutionContext) {
  def resolveSkills(extracted: List[ExtractedSkill]): Future[List[(Skill, Double)]] =
    Future.sequence(
      extracted.map { e =>
        skillRepo.findByNormalizedName(e.normalizedName).flatMap {
          case Some(existing) =>
            Future.successful((existing, e.confidence))
          case None =>
            val newSkill = Skill(
              id = UUID.randomUUID().toString,
              name = e.rawName,
              normalizedName = e.normalizedName,
              category = e.category
            )
            skillRepo.insert(newSkill).map(s => (s, e.confidence))
        }
      }
    )
}