package models

import java.time.OffsetDateTime

case class UserSkill(
                    id: String,
                    userId: String,
                    skillId: String,
                    rawScore: Double,
                    recencyWeightedScore: Double,
                    frequencyCount: Int,
                    appreciatorDiversity: Int,
                    compositeScore: Double,
                    appreciatorIds: String
//                    lastUpdatedAt: Option[String] = None
                    )