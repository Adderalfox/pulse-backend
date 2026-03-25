package models

import models.Role

case class JwtPayload(
                       userId: Long,
                       email: String,
                       role: Role,
                       companyId: Option[Long],
                       departmentId: Option[Long]
                     )