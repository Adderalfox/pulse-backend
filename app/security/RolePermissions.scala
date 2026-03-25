package security

object RolePermissions {

  val heirarchy: Map[String, Set[String]] = Map(
    "SUPER_ADMIN" -> Set("ADMIN"),
    "ADMIN" -> Set("HR"),
    "HR" -> Set("DEPARTMENT_MANAGER"),
    "DEPARTMENT_MANAGER" -> Set("TEAM_LEAD"),
    "TEAM_LEAD" -> Set("EMPLOYEE")
  )

  def canCreate(currentRole: String, targetRole: String): Boolean = {
    def getAllDescendants(role: String): Set[String] = {
      val children = heirarchy.getOrElse(role, Set())
      children ++ children.flatMap(getAllDescendants)
    }
    getAllDescendants(currentRole).contains(targetRole)
  }

  def hasAccess(userRole: String, allowedRoles: Set[String]): Boolean =
    allowedRoles.contains(userRole)
}