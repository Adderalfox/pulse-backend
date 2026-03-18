package utils

import com.redis._

object RedisClient {
  val client = new RedisClient("localhost", 6379)
}