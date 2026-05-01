package startup

import play.api.inject.ApplicationLifecycle
import utils.QdrantClientWrapper
import play.api.Logging

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class QdrantStartup @Inject()(qdrantClient: QdrantClientWrapper, lifecycle: ApplicationLifecycle)(implicit ec: ExecutionContext) extends Logging {
  logger.info("[QdrantStartup] Ensuring collection exists...")

  qdrantClient.ensureCollectionExists().map { _ =>
    logger.info("[QdrantStartup] Qdrant collection ready.")
  }.recover { case e =>
    logger.error(s"[QdrantStartup] Failed to ensure collection: ${e.getMessage}")
  }

  lifecycle.addStopHook{ () =>
    Future.successful(qdrantClient.close())
  }
}