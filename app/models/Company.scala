package models

import java.time.LocalDateTime

case class Company(
                  id: String,
                  name: String,
                  domain: String,
                  createdAt: LocalDateTime
                  )