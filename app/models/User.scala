package models

import models.Role

case class User(
               id: Long,
               name: String,
               email: String,
               password: String,
               role: Role,
               totalPoints: Int = 0,
               companyId: Option[Long],
               departmentId: Option[Long]
               )