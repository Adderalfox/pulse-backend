package models

import java.time.LocalDateTime

case class Department(
                     id: Long = 0,
                     name: String,
                     companyId: Long,
                     createdAt: Option[LocalDateTime] = None
                     )