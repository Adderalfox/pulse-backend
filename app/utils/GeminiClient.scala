package utils

import io.github.bucket4j.{Bandwidth, Bucket}
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}

import java.time.Duration
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Random
import play.api.Logging

sealed trait EmbeddingTaskType {
  def value: String
}

object EmbeddingTaskType {
  case object RETRIEVAL_DOCUMENT extends EmbeddingTaskType {
    val value = "RETRIEVAL_DOCUMENT"
  }

  case object RETRIEVAL_QUERY extends EmbeddingTaskType {
    val value = "RETRIEVAL_QUERY"
  }
}

sealed trait GeminiError

case class GeminiRateLimitError(message: String) extends GeminiError

case class GeminiInvalidRequestError(message: String) extends GeminiError

case class GeminiParseError(message: String) extends GeminiError

case class GeminiServerError(message: String) extends GeminiError

@Singleton
class GeminiClient @Inject()(ws: WSClient, config: AppConfig)(implicit ec: ExecutionContext) extends Logging {
  private val bucket: Bucket = Bucket.builder().addLimit(Bandwidth.builder()
      .capacity(config.gemini.requestPerMinute)
      .refillGreedy(config.gemini.requestPerMinute, Duration.ofMinutes(1))
      .build()
    )
    .build()

  def generateContent(systemPrompt: String, userPrompt: String, temperature: Double = 0.1): Future[Either[GeminiError, String]] =
    acquireToken().flatMap { _ =>
//      val url = s"${config.gemini.baseUrl}/models/${config.gemini.extractionModel}:generateContent?key=${config.gemini.apiKey}"
      val url = s"${config.gemini.baseUrlLocal}/generate"
      val body = buildGenerationRequestBody(systemPrompt, userPrompt, temperature)
      retryWithBackoff(config.gemini.maxRetries) {
        ws.url(url)
          .withHttpHeaders("Content-Type" -> "application/json")
          .post(body)
          .map(parseGenerationResponse)
      }
    }

  def embedText(text: String, taskType: EmbeddingTaskType): Future[Either[GeminiError, Seq[Float]]] =
    acquireToken().flatMap { _ =>
      val url = s"${config.gemini.baseUrl}/models/${config.gemini.embeddingModel}:embedContent?key=${config.gemini.apiKey}"
      val body = buildEmbeddingRequestBody(text, taskType)
      retryWithBackoff(config.gemini.maxRetries) {
        ws.url(url)
          .withHttpHeaders("Content-Type" -> "application/json")
          .post(body)
          .map(parseEmbeddingResponse)
      }
    }

  private def acquireToken(): Future[Unit] = Future {
    var acquired = false
    while (!acquired) {
      if (bucket.tryConsume(1)) acquired = true
      else Thread.sleep(200)
    }
  }(ec)

  private def buildGenerationRequestBody(systemPrompt: String, userPrompt: String, temperature: Double): JsValue = Json.obj(
    "system_instruction" -> Json.obj(
      "parts" -> Json.arr(Json.obj("text" -> systemPrompt))
    ),
    "contents" -> Json.arr(Json.obj(
      "role" -> "user", "parts" -> Json.arr(Json.obj("text" -> userPrompt)))
    ),
    "generationConfig" -> Json.obj(
      "temperature" -> temperature,
      "responseMimeType" -> "application/json"
    )
  )

  private def buildEmbeddingRequestBody(text: String, taskType: EmbeddingTaskType): JsValue = Json.obj(
    "model" -> s"models/${config.gemini.embeddingModel}",
    "content" -> Json.obj("parts" -> Json.arr(Json.obj("text" -> text))),
    "taskType" -> taskType.value
  )

  private def parseGenerationResponse(response: WSResponse): Either[GeminiError, String] =
    response.status match {
      case 200 =>
        logger.info("Successfully generated content from Gemini model")
        val text = (response.json \ "candidates" \ 0 \ "content" \ "parts" \ 0 \ "text").asOpt[String]
        text match {
          case Some(t) => Right(t)
          case None => Left(GeminiParseError(s"Unexpected response shape: ${response.body.take(300)}"))
        }
      case 400 => Left(GeminiInvalidRequestError(s"Bad request: ${response.body.take(300)}"))
      case 429 => Left(GeminiRateLimitError("Rate limited by Gemini API"))
      case s => Left(GeminiServerError(s"HTTP $s: ${response.body.take(300)}"))
    }

  private def parseEmbeddingResponse(response: WSResponse): Either[GeminiError, Seq[Float]] =
    response.status match {
      case 200 =>
        logger.info("Successfully reached Gemini model for embedding")
        (response.json \ "embedding" \ "values").asOpt[Seq[Float]] match {
          case Some(v) => Right(v)
          case None => Left(GeminiParseError("No embedding values in response"))
        }
      case 400 => Left(GeminiInvalidRequestError(response.body.take(300)))
      case 429 => Left(GeminiRateLimitError("Rate limited by Gemini API"))
      case s => Left(GeminiServerError(s"HTTP $s"))
    }

  private def retryWithBackoff[A](retriesLeft: Int)(attempt: => Future[Either[GeminiError, A]]): Future[Either[GeminiError, A]] =
    attempt.flatMap {
      case Left( _: GeminiRateLimitError | _: GeminiServerError) if retriesLeft > 0 =>
        val attemptNum = config.gemini.maxRetries - retriesLeft
        val delay = (config.gemini.retryBaseDelayMs * Math.pow(2, attemptNum)).toLong + Random.nextInt(100)
        after(delay.milliseconds)(retryWithBackoff(retriesLeft - 1)(attempt))
      case other => Future.successful(other)
    }

  private def after[A](duration: FiniteDuration)(f: => Future[A]): Future[A] = {
    val promise = scala.concurrent.Promise[A]()
    val runnable: Runnable = () => f.onComplete(promise.complete)
    val scheduler = java.util.concurrent.Executors.newScheduledThreadPool(1)
    scheduler.schedule(runnable, duration.toMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
    promise.future
  }
  // NOTE: In production inject ActorSystem and use akka.pattern.after instead.
}