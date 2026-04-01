package models

import java.time.LocalDateTime

case class Department(
                     id: String,
                     name: String,
                     companyId: Option[String],
                     createdAt: Option[LocalDateTime] = None
                     )