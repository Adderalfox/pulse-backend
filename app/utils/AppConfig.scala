package utils

import javax.inject._
import play.api.Configuration

@Singleton
class AppConfig @Inject()(config: Configuration) {

  object gemini {
    val apiKey: String = config.get[String]("gemini.api-key")
    val baseUrl: String = config.get[String]("base-url")
    val extractionModel: String = config.get[String]("extraction-model")
    val embeddingModel: String = config.get[String]("embedding-model")
    val maxRetries: Int = config.get[Int]("max-retries")
    val retryBaseDelayMs: Int = config.get[Int]("retry-base-delay-ms")
    val requestPerMinute: Int = config.get[Int]("requests-per-minute")
  }

  object qdrant {
    val host: String = config.get[String]("host")
    val port: Int = config.get[Int]("port")
    val collectionName: String = config.get[String]("collection-name")
    val vectorDimension: Int = config.get[Int]("vector-dimensions")
  }
}