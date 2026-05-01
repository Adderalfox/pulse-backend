package utils

import javax.inject._
import play.api.Configuration

@Singleton
class AppConfig @Inject()(config: Configuration) {

  object gemini {
    val apiKey: String = config.get[String]("gemini.api-key")
    val baseUrl: String = config.get[String]("gemini.base-url")
    val extractionModel: String = config.get[String]("gemini.extraction-model")
    val embeddingModel: String = config.get[String]("gemini.embedding-model")
    val maxRetries: Int = config.get[Int]("gemini.max-retries")
    val retryBaseDelayMs: Int = config.get[Int]("gemini.retry-base-delay-ms")
    val requestPerMinute: Int = config.get[Int]("gemini.requests-per-minute")
  }

  object qdrant {
    val host: String = config.get[String]("qdrant.host")
    val port: Int = config.get[Int]("qdrant.port")
    val collectionName: String = config.get[String]("qdrant.collection-name")
    val vectorDimension: Int = config.get[Int]("qdrant.vector-dimensions")
  }
}