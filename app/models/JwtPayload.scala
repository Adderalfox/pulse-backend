package models

import models.Role

case class JwtPayload(
                       userId: String,
                       email: String,
                       role: Role,
                       companyId: Option[String],
                       departmentId: Option[String]
                     )