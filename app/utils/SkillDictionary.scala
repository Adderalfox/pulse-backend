package utils

import models.SkillCategory
import dto.ExtractedSkill
import SkillCategory._

object SkillDictionary {

  val entries: Map[String, (String, SkillCategory, Double)] = Map(
    "postgresql"      -> ("PostgreSQL", TECHNICAL, 0.85),
    "postgres"        -> ("PostgreSQL", TECHNICAL, 0.85),
    "mysql"           -> ("MySQL", TECHNICAL, 0.85),
    "mongodb"         -> ("MongoDB", TECHNICAL, 0.85),
    "scala"           -> ("Scala", TECHNICAL, 0.90),
    "java"            -> ("Java", TECHNICAL, 0.85),
    "python"          -> ("Python", TECHNICAL, 0.85),
    "kafka"           -> ("Apache Kafka", TECHNICAL, 0.85),
    "kubernetes"      -> ("Kubernetes", TECHNICAL, 0.85),
    "k8s"             -> ("Kubernetes", TECHNICAL, 0.85),
    "docker"          -> ("Docker", TECHNICAL, 0.85),
    "aws"             -> ("AWS", TECHNICAL, 0.85),
    "redis"           -> ("Redis", TECHNICAL, 0.85),
    "api"             -> ("API Design", TECHNICAL, 0.65),
    "rest"            -> ("REST APIs", TECHNICAL, 0.70),
    "grpc"            -> ("gRPC", TECHNICAL, 0.85),
    "leadership"      -> ("Leadership", BEHAVIORAL, 0.75),
    "mentoring"       -> ("Mentoring", BEHAVIORAL, 0.80),
    "mentor"          -> ("Mentoring", BEHAVIORAL, 0.80),
    "communication"   -> ("Communication", BEHAVIORAL, 0.70),
    "collaboration"   -> ("Collaboration", BEHAVIORAL, 0.70),
    "problem solving" -> ("Problem Solving", BEHAVIORAL, 0.70),
    "teamwork"        -> ("Teamwork", BEHAVIORAL, 0.70),
    "ownership"       -> ("Ownership", BEHAVIORAL, 0.75),
    "initiative"      -> ("Initiative", BEHAVIORAL, 0.70),
    "debugging"       -> ("Debugging", TECHNICAL, 0.75),
    "code review"     -> ("Code Review", TECHNICAL, 0.75),
    "architecture"    -> ("System Architecture", DOMAIN, 0.80),
    "migration"       -> ("Database Migration", DOMAIN, 0.70),
    "performance"     -> ("Performance Optimization", DOMAIN, 0.65),
    "scalability"     -> ("Scalability", DOMAIN, 0.65),
    "security"        -> ("Security", DOMAIN, 0.70)
  )

  def extract(message: String): List[ExtractedSkill] = {
    val lower = message.toLowerCase
    entries.collect {
      case (keyword, (name, category, confidence)) if lower.contains(keyword) =>
        ExtractedSkill(
          rawName = name,
          normalizedName =  keyword.replaceAll("\\s+", "-"),
          category = category,
          confidence = confidence
        )
    }
      .toList
      .groupBy(_.normalizedName)
      .values
      .map(_.maxBy(_.confidence))
      .toList
  }
}