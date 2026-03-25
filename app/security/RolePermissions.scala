package security

import models.Role

object RolePermissions {

  val hierarchy: Map[Role, Set[Role]] = Map(
    Role.SUPER_ADMIN -> Set(Role.ADMIN),
    Role.ADMIN -> Set(Role.HR),
    Role.HR -> Set(Role.DEPARTMENT_MANAGER),
    Role.DEPARTMENT_MANAGER -> Set(Role.TEAM_LEAD),
    Role.TEAM_LEAD -> Set(Role.EMPLOYEE)
  )

  def canCreate(currentRole: Role, targetRole: Role): Boolean = {

    def getAllDescendants(role: Role): Set[Role] = {
      val children = hierarchy.getOrElse(role, Set.empty)
      children ++ children.flatMap(getAllDescendants)
    }

    getAllDescendants(currentRole).contains(targetRole)
  }

  def hasAccess(userRole: Role, allowedRoles: Set[Role]): Boolean =
    allowedRoles.contains(userRole)
}