package services

import models.SkillCategory
import dto.{ExtractedSkill, ExtractionResult, ExtractionSource}
import play.api.Logging
import utils.{AppConfig, GeminiClient, SkillDictionary}
import io.circe.parser._
import io.circe.generic.auto._

import javax.inject._
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class SkillExtractionService @Inject()(geminiClient: GeminiClient, config: AppConfig)(implicit ec: ExecutionContext) extends Logging {
  private val systemPrompt =
    """You are a professional skills extraction engine for an employee recognition platform.
      |Your only job is to analyze appreciation messages and extract professional skills.
      |
      |Rules you must follow without exception:
      |1. Return ONLY a JSON array. No preamble, no explanation, no markdown fences.
      |2. Each element must have exactly these fields: "skill", "normalizedName", "category", "confidence"
      |3. "skill" is the display name with correct casing (e.g. "PostgreSQL", "Active Listening")
      |4. "normalizedName" is lowercase, trimmed, hyphens only (e.g. "postgresql", "active-listening")
      |5. "category" must be exactly one of: "TECHNICAL", "BEHAVIORAL", "DOMAIN"
      |6. "confidence" is a float 0.0 to 1.0. Only include skills with confidence >= 0.5
      |7. Maximum 8 skills per message. Pick the most confident if more exist.
      |8. De-duplicate: same concept = return only the higher-confidence entry.
      |9. Do not invent skills not supported by the message text.""".stripMargin

  def extract(message: String, senderRole: String, recipientRole: String, department: String): Future[ExtractionResult] = {
    val userPrompt =
      s"""Extract skills from this appreciation message:
         |
         |"$message"
         |
         |Sender context: $senderRole in $department
         |Recipient context: $recipientRole in $department""".stripMargin

    geminiClient.generateContent(systemPrompt, userPrompt).map {
      case Right(rawJson) =>
        logger.info(s"Successfully reached Gemini model for skill extraction")
        parseGeminiResponse(rawJson) match {
          case Right(skills) =>
            ExtractionResult(skills, config.gemini.extractionModel, ExtractionSource.LLM)
          case Left(err) =>
            logger.warn(s"Gemini parse error, falling back to dictionary: $err")
            fallback(message)
        }
      case Left(err) =>
        logger.warn(s"Gemini call failed, falling back to dictionary: $err")
        fallback(message)
    }
  }

  def extractFromQuery(query:String): Future[ExtractionResult] =
    extract(query, "employee", "employee", "unknown")

  private case class GeminiSkillEntry(
                                       skill: String,
                                       normalizedName: String,
                                       category: String,
                                       confidence: Double
                                     )

  private def parseGeminiResponse(rawJson: String): Either[String, List[ExtractedSkill]] =
    parse(rawJson).flatMap(_.as[List[GeminiSkillEntry]]) match {
      case Right(entries) =>
      Right(
        entries
          .filter(_.confidence >= 0.5)
          .take(8)
          .map { e =>
            ExtractedSkill(
              rawName = e.skill,
              normalizedName = e.normalizedName,
              category = SkillCategory.fromString(e.category),
              confidence = e.confidence
            )
          }
      )
      case Left(err) => Left(err.getMessage)
    }

  private def fallback(message: String): ExtractionResult =
    ExtractionResult(
      skills = SkillDictionary.extract(message),
      modelUsed = "dictionary-fallback",
      source = ExtractionSource.FALLBACK
    )
}