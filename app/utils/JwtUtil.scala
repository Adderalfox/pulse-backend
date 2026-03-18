package utils

import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import com.typesafe.config.ConfigFactory
import java.time.Clock

object JwtUtil {
  private val config = ConfigFactory.load()
  private val secretKey = config.getString("jwt.secret")
  private val issuer = config.getString("jwt.issuer")
  private val expiry = config.getLong("jwt.expiry-seconds")

  implicit val clock: Clock = Clock.systemUTC()

  def generateToken(userId: Long): String = {
    val claim = JwtClaim(
      content = s"""{"userId": $userId}"""
    )
      .issuedNow
      .expiresIn(expiry)
      .startsNow
      .by(issuer)

    Jwt.encode(claim, secretKey, JwtAlgorithm.HS256)
  }

  def isValid(token: String): Boolean = {
    Jwt.isValid(token, secretKey, Seq(JwtAlgorithm.HS256))
  }
}