package models

import java.time.LocalDateTime

sealed trait SkillCategory { def name: String }
object SkillCategory {
  case object TECHNICAL extends SkillCategory { val name = "TECHNICAL" }
  case object BEHAVIORAL extends SkillCategory { val name = "BEHAVIORAL" }
  case object DOMAIN extends SkillCategory { val name = "DOMAIN" }

  def fromString(s: String): SkillCategory = s.toUpperCase match {
    case "TECHNICAL" => TECHNICAL
    case "BEHAVIORAL" => BEHAVIORAL
    case "DOMAIN" => DOMAIN
    case other => throw new IllegalArgumentException(s"Unknown SkillCategory: $other")
  }
}

case class Skill(
                id: String,
                name: String,
                normalizedName: String,
                category: SkillCategory,
                createdAt: Option[LocalDateTime] = None
                )