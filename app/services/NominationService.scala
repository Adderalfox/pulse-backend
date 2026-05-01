package services

import models.NominationDraft
import repositories.{NominationDraftRepository, UserSkillRepository, SkillRepository}
import utils.{EmbeddingTaskType, GeminiClient, QdrantClientWrapper}
import play.api.Logging

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NominationService @Inject()(
                                   geminiClient: GeminiClient,
                                   qdrantClient: QdrantClientWrapper,
                                   userSkillRepo: UserSkillRepository,
                                   skillRepo: SkillRepository,
                                   nominationDraftRepo: NominationDraftRepository
                                 )(implicit ec: ExecutionContext) extends Logging {

  private val awardQueryTemplates: Map[String, String] = Map(
    "innovation" -> "creative problem solving innovative solution architecture new approach",
    "mentorship" -> "mentoring coaching guidance helped team members teaching knowledge sharing",
    "leadership" -> "led the team decision making ownership accountability direction",
    "collaboration" -> "cross-team worked together partnership coordination aligned stakeholders",
    "excellence" -> "outstanding quality delivery high performance reliable consistent results",
    "culture" -> "positive attitude morale team spirit values inclusion supportive"
  )

  def generateDraft(nomineeId: String, requestedBy: String, awardCategory: String, nomineeName: String, nomineeDepartment: String): Future[Either[String, NominationDraft]] = {

    val categoryKey = awardCategory.toLowerCase.trim
    val retrievalQuery = awardQueryTemplates.getOrElse(
      categoryKey,
      awardCategory
    )

    geminiClient.embedText(retrievalQuery, EmbeddingTaskType.RETRIEVAL_QUERY).flatMap {
      case Left(err) =>
        Future.successful(Left(s"Failed to embed award query: $err"))

      case Right(queryVector) =>

        qdrantClient.searchForNominee(
          queryVector = queryVector,
          nomineeId = nomineeId,
          limit = 8,
          scoreThreshold = 0.55f
        ).flatMap { relevantPoints =>

          if (relevantPoints.isEmpty) {
            Future.successful(Left(s"No relevant recognition evidence found for nominee $nomineeId"))
          } else {
            val retrievedMessages = relevantPoints.flatMap { p =>
              Option(p.getPayload.get("message_preview")).map(_.getStringValue)
            }
            val retrievedSkills = relevantPoints
              .flatMap(p => Option(p.getPayload.get("extracted_skills")).map(_.getStringValue))
              .flatMap(_.split(",").toList)
              .map(_.trim)
              .filter(_.nonEmpty)
              .distinct

            userSkillRepo.getTopSkillsForUser(nomineeId, 5).flatMap { topUserSkills =>
              val skillIdList = topUserSkills.map(_.skillId).toList

              Future.sequence(skillIdList.map(id => skillRepo.findById(id))).flatMap { skillOpts =>
                val topSkillNames = skillOpts.flatten.map(_.name).toList

                val ragContext = buildRagContext(
                  nomineeName = nomineeName,
                  department = nomineeDepartment,
                  awardCategory = awardCategory,
                  retrievedMessages = retrievedMessages,
                  retrievedSkills = retrievedSkills,
                  topSkillNames = topSkillNames,
                  appreciationCount = relevantPoints.size
                )

                generateWithGemini(ragContext, nomineeName, awardCategory).flatMap {
                  case Left(err) =>
                    Future.successful(Left(s"Draft generation failed: $err"))

                  case Right(draftText) =>
                    val draft = NominationDraft(
                      id = UUID.randomUUID().toString,
                      nomineeId = nomineeId,
                      requestedBy = requestedBy,
                      awardCategory = awardCategory,
                      draftText = draftText,
                      skillsCited = retrievedSkills.take(5).mkString(","),
                      appreciationsUsed = relevantPoints.size
                    )
                    nominationDraftRepo.insert(draft).map(d => Right(d))
                }
              }
            }
          }
        }
    }
  }

  def getDraftsForNominee(nomineeId: String): Future[Seq[NominationDraft]] =
    nominationDraftRepo.findByNominee(nomineeId)

  def getDraftsByRequester(requestedBy: String): Future[Seq[NominationDraft]] =
    nominationDraftRepo.findByRequester(requestedBy)

  private def buildRagContext(
                               nomineeName: String,
                               department: String,
                               awardCategory: String,
                               retrievedMessages: List[String],
                               retrievedSkills: List[String],
                               topSkillNames: List[String],
                               appreciationCount: Int
                             ): String = {
    val evidenceBlock = retrievedMessages.zipWithIndex.map { case (msg, i) =>
      s"  ${i + 1}. \"$msg\""
    }.mkString("\n")

    s"""Nominee: $nomineeName
       |Department: $department
       |Award being considered: $awardCategory
       |
       |Most relevant peer recognitions (retrieved for this award category):
       |$evidenceBlock
       |
       |Skills mentioned in these recognitions: ${retrievedSkills.distinct.take(8).mkString(", ")}
       |Nominee's top skills by composite peer-recognition score: ${topSkillNames.mkString(", ")}
       |Total relevant appreciations found: $appreciationCount""".stripMargin
  }

  private def generateWithGemini(
                                  ragContext: String,
                                  nomineeName: String,
                                  awardCategory: String
                                ): Future[Either[String, String]] = {

    val systemPrompt =
      s"""You are an HR writing assistant helping managers submit award nominations.
         |Generate a professional, specific, and compelling nomination paragraph.
         |
         |Rules:
         |1. Write 3-4 sentences maximum.
         |2. Cite specific skills and behaviors from the evidence provided — do not invent any.
         |3. Do not use generic filler phrases like "always goes above and beyond".
         |4. Reference concrete evidence from the peer recognitions provided.
         |5. Tone: professional, warm, specific.
         |6. Return only the nomination paragraph text — no headers, no bullet points.""".stripMargin

    val userPrompt =
      s"""Based ONLY on the following peer recognition evidence, write a nomination paragraph
         |for $nomineeName for the $awardCategory Award:
         |
         |$ragContext""".stripMargin

    geminiClient.generateContent(systemPrompt, userPrompt, temperature = 0.3).map {
      case Right(text) => Right(text.trim)
      case Left(err) => Left(err.toString)
    }
  }
}