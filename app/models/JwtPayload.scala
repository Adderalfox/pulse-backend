package models

case class JwtPayload(
                       userId: Long,
                       email: String,
                       role: String,
                       companyId: Option[Long],
                       departmentId: Option[Long]
                     )