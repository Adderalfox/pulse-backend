package models

import java.time.LocalDateTime

case class AppreciationSkill(
                            id: String,
                            appreciationId: String,
                            skillId: String,
                            llmConfidence: Double,
                            extractionModel: String,
                            extractedAt: Option[LocalDateTime] = None
                            )