package dto

import models.SkillCategory

sealed trait ExtractionSource
object ExtractionSource {
  case object LLM extends ExtractionSource
  case object FALLBACK extends ExtractionSource
}

case class ExtractedSkill(
                          rawName: String,
                          normalizedName: String,
                          category: SkillCategory,
                          confidence: Double
                          )

case class ExtractionResult(
                           skills: List[ExtractedSkill],
                           modelUsed: String,
                           source: ExtractionSource
                           )