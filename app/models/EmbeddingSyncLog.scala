package models

import java.time.LocalDateTime

case class EmbeddingSyncLog(
                           appreciationId: String,
                           qdrantPointId: String,
                           modelVersion: String,
                           embeddedAt: Option[LocalDateTime] = None
                           )