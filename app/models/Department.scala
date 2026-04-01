package models

import java.time.LocalDateTime

case class Department(
                     id: Long = 0,
                     name: String,
                     companyId: Option[Long],
                     createdAt: Option[LocalDateTime] = None
                     )