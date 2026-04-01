package models

import java.time.LocalDateTime

case class Department(
                     id: String,
                     name: String,
                     companyId: Option[Long],
                     createdAt: Option[LocalDateTime] = None
                     )