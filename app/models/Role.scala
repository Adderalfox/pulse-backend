package models

sealed trait Role {
  def name: String
}

object Role {
  case object SUPER_ADMIN extends Role {
    val name = "SUPER_ADMIN"
  }

  case object ADMIN extends Role {
    val name = "ADMIN"
  }

  case object HR extends Role {
    val name = "HR"
  }

  case object DEPARTMENT_MANAGER extends Role {
    val name = "DEPARTMENT_MANAGER"
  }

  case object TEAM_LEAD extends Role {
    val name = "TEAM_LEAD"
  }

  case object EMPLOYEE extends Role {
    val name = "EMPLOYEE"
  }

  def fromString(role: String): Role = role match {
    case "SUPER_ADMIN" => SUPER_ADMIN
    case "ADMIN" => ADMIN
    case "HR" => HR
    case "DEPARTMENT_MANAGER" => DEPARTMENT_MANAGER
    case "TEAM_LEAD" => TEAM_LEAD
    case "EMPLOYEE" => EMPLOYEE
    case _ => throw new IllegalArgumentException("Invalid role")
  }
}