package services

import models.insights.ExpertQueryResult
import play.api.Logging
import utils.{AppConfig, EmbeddingTaskType, GeminiClient, QdrantClientWrapper}
import repositories.{UserSkillRepository, SkillRepository}
import io.qdrant.client.grpc.Points.ScoredPoint

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

@Singleton
class RagService @Inject()(
                            geminiClient: GeminiClient,
                            qdrantClient: QdrantClientWrapper,
                            skillExtractionService: SkillExtractionService,
                            userSkillRepo: UserSkillRepository,
                            skillRepo: SkillRepository,
                            config: AppConfig
                          )(implicit ec: ExecutionContext) extends Logging {

  def findExpertsForQuery(query: String, limit: Int = 5): Future[Either[String, ExpertQueryResult]] =
    for {
      extraction <- skillExtractionService.extractFromQuery(query)
      skillNames = extraction.skills.map(_.normalizedName)
      vectorResult <- geminiClient.embedText(query, EmbeddingTaskType.RETRIEVAL_QUERY)

      result <- vectorResult match {
        case Left(err) => Future.successful(Left(s"Embedding failed: $err"))
        case Right(queryVector) =>
          for {
            qdrantResults <- qdrantClient.search(
              queryVector = queryVector,
              limit = 50,
              scoreThreshold = 0.60f,
              skillFilter = if (skillNames.nonEmpty) Some(skillNames) else None
            )
            userEvidence = groupByRecipient(qdrantResults)
            userIds = userEvidence.keys.toList
            skillIds <- resolveSkillIds(skillNames)
            dbScores <- userSkillRepo.getCountsByDepartmentUsers(userIds)
              .map(_.groupBy(_.userId).view.mapValues { skills =>
                val relevant = skills.filter(s => skillIds.contains(s.skillId))
                if (relevant.isEmpty) 0.0 else relevant.map(_.compositeScore).max
              }.toMap)
            ranked = rankAndLimit(userEvidence, dbScores, limit)
            ragContext = buildRagContext(ranked, userEvidence)
            explanations <- generateExplanations(query, ragContext, ranked.map(_._1))
            experts = ranked.zip(explanations).map { case ((userId, _), explanation) =>
              val evidence = userEvidence(userId)
              models.insights.ExpertResult(
                userId = userId,
                displayName = userId, // caller resolves to name if needed
                compositeScore = dbScores.getOrElse(userId, 0.0),
                frequencyCount = evidence.size,
                appreciatorDiversity = evidence
                  .flatMap(p => Option(p.getPayload.get("sender_id")))
                  .map(_.getStringValue).distinct.size,
                explanation = explanation
              )
            }
          } yield Right(ExpertQueryResult(query, skillNames, experts))
      }
    } yield result

  private def groupByRecipient(points: List[ScoredPoint]): Map[String, List[ScoredPoint]] =
    points
      .flatMap(p => Option(p.getPayload.get("recipient_id")).map(v => v.getStringValue -> p))
      .groupBy(_._1)
      .view.mapValues(_.map(_._2))
      .toMap

  private def resolveSkillIds(normalizedNames: List[String]): Future[Set[String]] =
    Future.sequence(normalizedNames.map(n => skillRepo.findByNormalizedName(n)))
      .map(_.flatten.map(_.id).toSet)

  private def rankAndLimit(
                            userEvidence: Map[String, List[ScoredPoint]],
                            dbScores: Map[String, Double],
                            limit: Int
                          ): List[(String, Float)] =
    userEvidence.map { case (userId, points) =>
        val avgVectorScore = points.map(_.getScore).sum / points.size
        val combined = (0.60f * avgVectorScore) + (0.40f * dbScores.getOrElse(userId, 0.0).toFloat)
        (userId, combined)
      }
      .toList
      .sortBy(-_._2)
      .take(limit)

  private def buildRagContext(
                               ranked: List[(String, Float)],
                               userEvidence: Map[String, List[ScoredPoint]]
                             ): String =
    ranked.map { case (userId, score) =>
      val points = userEvidence(userId).take(3)
      val previews = points.flatMap(p => Option(p.getPayload.get("message_preview")).map(_.getStringValue))
      val skills = points.flatMap(p => Option(p.getPayload.get("extracted_skills")).map(_.getStringValue))
        .flatMap(_.split(",")).distinct.take(5)
      s"""User ID: $userId
         |Key skills: ${skills.mkString(", ")}
         |Evidence:
         |${previews.zipWithIndex.map { case (p, i) => s"  ${i + 1}. $p" }.mkString("\n")}
         |Relevance score: $score""".stripMargin
    }.mkString("\n\n---\n\n")

  private def generateExplanations(
                                    query: String,
                                    ragContext: String,
                                    userIds: List[String]
                                  ): Future[List[String]] = {
    if (userIds.isEmpty) return Future.successful(Nil)

    val systemPrompt =
      """You are an HR intelligence assistant. Based ONLY on the evidence provided below,
        |explain in one concise sentence why each person is a good match for the query.
        |Return ONLY a JSON array of strings, one per person, in the same order as provided.
        |No preamble, no explanation, just the JSON array.""".stripMargin

    val userPrompt =
      s"""Query: "$query"
         |Candidates: ${userIds.zipWithIndex.map { case (id, i) => s"${i + 1}. $id" }.mkString(", ")}
         |Evidence:
         |$ragContext""".stripMargin

    geminiClient.generateContent(systemPrompt, userPrompt).map {
      case Right(json) =>
        io.circe.parser.parse(json).flatMap(_.as[List[String]])
          .getOrElse(userIds.map(_ => "Strong match based on peer recognition evidence."))
      case Left(_) =>
        userIds.map(_ => "Strong match based on peer recognition evidence.")
    }
  }
}