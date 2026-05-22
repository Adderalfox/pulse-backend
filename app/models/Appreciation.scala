package models

import models.Role

import java.time.LocalDateTime

case class Appreciation(
                         id: String,
                         giverId: String,
                         receiverId: String,
                         companyId: String,
                         departmentId: Option[String],
                         text: String,
                         appreciationType: String,
                         visibility: String,
                         pointsAwarded: Int = 0,
                         createdAt: Option[LocalDateTime] = None,
                         updatedAt: Option[LocalDateTime] = None
                       )

case class AppreciationSkillTag(
                                 id: String,
                                 appreciationId: String,
                                 skillName: String,
                                 confidenceScore: Double = 1.0,
                                 source: String = "manual",
                                 createdAt: Option[LocalDateTime] = None
                               )

case class InteractionEdge(
                            id: String,
                            userIdFrom: String,
                            userIdTo: String,
                            companyId: String,
                            interactionType: String,
                            weight: Double = 1.0,
                            lastSeenAt: Option[LocalDateTime] = None,
                            createdAt: Option[LocalDateTime] = None
                          )

case class UserSkillScore(
                           id: String,
                           userId: String,
                           companyId: String,
                           skillName: String,
                           score: Double = 0.0,
                           endorsementCount: Int = 0,
                           certified: Boolean = false,
                           updatedAt: Option[LocalDateTime] = None
                         )

case class AppreciationWithDetails(
                                    appreciation: Appreciation,
                                    giverName: String,
                                    giverRole: Role,
                                    receiverName: String,
                                    receiverRole: Role,
                                    skillTags: Seq[AppreciationSkillTag]
                                  )