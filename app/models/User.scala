package models

case class User(
               id: Long,
               name: String,
               email: String,
               password: String,
               role: String,
               totalPoints: Int = 0
               )