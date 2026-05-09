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

  /**
   * Generates content using the configured extraction model.
   * Dispatches to either local Ollama or Gemini API based on model name.
   */
  def generateContent(systemPrompt: String, userPrompt: String, temperature: Double = 0.1): Future[Either[GeminiError, String]] =
    acquireToken().flatMap { _ =>
      val model = config.gemini.extractionModel
      val (url, body, parser) = if (isOllamaModel(model)) {
        (
          s"${config.gemini.baseUrlLocal}/generate",
          buildOllamaRequestBody(model, systemPrompt, userPrompt, temperature),
          parseOllamaResponse _
        )
      } else {
        (
          s"${config.gemini.baseUrl}/models/$model:generateContent?key=${config.gemini.apiKey}",
          buildGeminiRequestBody(systemPrompt, userPrompt, temperature),
          parseGeminiResponse _
        )
      }

      retryWithBackoff(config.gemini.maxRetries) {
        ws.url(url)
          .withHttpHeaders("Content-Type" -> "application/json")
          .post(body)
          .map(parser)
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

  private def isOllamaModel(model: String): Boolean =
    model.contains(":") || model.toLowerCase.startsWith("qwen") || model.toLowerCase.contains("llama")

  private def acquireToken(): Future[Unit] = Future {
    var acquired = false
    while (!acquired) {
      if (bucket.tryConsume(1)) acquired = true
      else Thread.sleep(200)
    }
  }(ec)

  // --- Request Builders ---

  private def buildGeminiRequestBody(systemPrompt: String, userPrompt: String, temperature: Double): JsValue = Json.obj(
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

  private def buildOllamaRequestBody(model: String, systemPrompt: String, userPrompt: String, temperature: Double): JsValue = {
    // Combine system and user prompt for better compatibility with some local models
    val combinedPrompt = s"System Instruction:\n$systemPrompt\n\nUser Input:\n$userPrompt"
    Json.obj(
      "model" -> model,
      "prompt" -> combinedPrompt,
      "stream" -> false,
      "options" -> Json.obj(
        "temperature" -> temperature,
        "num_predict" -> 300, // Increase token limit to prevent truncated JSON
        "num_ctx" -> 1024
      ),
      "format" -> "json",
      "think" -> false
    )
  }

  private def buildEmbeddingRequestBody(text: String, taskType: EmbeddingTaskType): JsValue = Json.obj(
    "model" -> s"models/${config.gemini.embeddingModel}",
    "content" -> Json.obj("parts" -> Json.arr(Json.obj("text" -> text))),
    "taskType" -> taskType.value
  )

  // --- Response Parsers ---

  private def parseGeminiResponse(response: WSResponse): Either[GeminiError, String] =
    response.status match {
      case 200 =>
        logger.info("Successfully generated content from Gemini model")
        val text = (response.json \ "candidates" \ 0 \ "content" \ "parts" \ 0 \ "text").asOpt[String]
        text.map(t => Right(cleanJsonResponse(t))).getOrElse {
          logger.error(s"Unexpected Gemini response shape. Body: ${response.body.take(500)}")
          Left(GeminiParseError(s"Unexpected Gemini response shape"))
        }
      case 400 => Left(GeminiInvalidRequestError(s"Bad request: ${response.body.take(300)}"))
      case 429 => Left(GeminiRateLimitError("Rate limited by Gemini API"))
      case s => Left(GeminiServerError(s"Gemini HTTP $s: ${response.body.take(300)}"))
    }

  private def parseOllamaResponse(response: WSResponse): Either[GeminiError, String] =
    response.status match {
      case 200 =>
        val json = response.json
        val responseText = (json \ "response").asOpt[String].filter(_.trim.nonEmpty)
        val thinkingText = (json \ "thinking").asOpt[String].filter(_.trim.nonEmpty)
        val finalRawText = responseText.getOrElse(thinkingText.getOrElse(""))
        val cleanedText = cleanJsonResponse(finalRawText)

        // ADD THIS - log the full raw response before cleaning
        logger.info(s"Ollama raw response field (FULL): $finalRawText")
        logger.info(s"Ollama done_reason: ${(json \ "done_reason").asOpt[String]}")

        if (cleanedText.isEmpty) {
          Left(GeminiParseError("Ollama returned an empty response string"))
        } else {
          Right(cleanedText)
        }
      case s =>
        Left(GeminiServerError(s"Ollama HTTP $s"))
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

  /**
   * Strips markdown code fences (e.g., ```json ... ```) from LLM responses
   * to ensure robust JSON parsing.
   */
  private def cleanJsonResponse(raw: String): String = {
    val trimmed = raw.trim

    val withoutThink = if (trimmed.contains("<think>")) {
      val thinkRegex = """(?s)<think>.*?</think>""".r
      thinkRegex.replaceAllIn(trimmed, "").trim
    } else trimmed

    val stripped = if (withoutThink.startsWith("```")) {
      val jsonBlockRegex = """(?s)```(?:json)?\s*(.*?)\s*```""".r
      jsonBlockRegex.findFirstMatchIn(withoutThink) match {
        case Some(m) => m.group(1).trim
        case None => withoutThink.stripPrefix("```json").stripPrefix("```").stripSuffix("```").trim
      }
    } else withoutThink

    repairJson(stripped)
  }

  /**
   * Best-effort repair of common small-model JSON corruption.
   * Handles mangled keys, escaped quotes in wrong places, unclosed braces.
   */
  private def repairJson(raw: String): String = {
    // Fix Qwen3's specific mangling pattern:
    // `": ":\n  "partialSummary\": \"value\""`
    // caused by the model losing track of key vs value context
    val fixMangledKeySeparator = raw
      .replaceAll("""(?m)"\s*":\s*":\s*\n\s*"""", "\"")          // `": ":\n  "` -> `"`
      .replaceAll("""\\\"([\w]+)\\\":\s*\\\"""", """"$1": """")   // `\"key\": \"` -> `"key": "`
      .replaceAll("""\\"""", "\"")                                  // remaining `\"` -> `"`

    // Close any unclosed braces/brackets
    val openBraces   = fixMangledKeySeparator.count(_ == '{') - fixMangledKeySeparator.count(_ == '}')
    val openBrackets = fixMangledKeySeparator.count(_ == '[') - fixMangledKeySeparator.count(_ == ']')

    fixMangledKeySeparator +
      ("]" * Math.max(0, openBrackets)) +
      ("}" * Math.max(0, openBraces))
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