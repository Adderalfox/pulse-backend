package utils

import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import com.typesafe.config.ConfigFactory
import java.time.Clock
import models.JwtPayload
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.auto._

object JwtUtil {
  private val config = ConfigFactory.load()
  private val secretKey = config.getString("jwt.secret")
  private val issuer = config.getString("jwt.issuer")
  private val expiry = config.getLong("jwt.expiry-seconds")

  implicit val clock: Clock = Clock.systemUTC()

  def generateToken(payload: JwtPayload): String = {
    val claim = JwtClaim(
      content = payload.asJson.noSpaces
    )
      .issuedNow
      .expiresIn(expiry)
      .startsNow
      .by(issuer)

    Jwt.encode(claim, secretKey, JwtAlgorithm.HS256)
  }

  def decodeToken(token: String): Option[JwtPayload] = {
    val decodedClaim = Jwt.decode(token, secretKey, Seq(JwtAlgorithm.HS256)).toOption

    decodedClaim match {
      case Some(claim) =>
        decode[JwtPayload](claim.content) match {
          case Right(payload) => Some(payload)
          case Left(_) => None
        }
      case None => None
    }
  }

  def isValid(token: String): Boolean = {
    Jwt.isValid(token, secretKey, Seq(JwtAlgorithm.HS256))
  }
}