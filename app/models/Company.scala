package models

import java.time.LocalDateTime

case class Company(
                  id: Long = 0,
                  name: String,
                  domain: String,
                  createdAt: Option[LocalDateTime] = None
                  )