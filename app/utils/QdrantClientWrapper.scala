package utils

import com.google.common.util.concurrent.{FutureCallback, Futures, ListenableFuture, MoreExecutors}
import io.qdrant.client.QdrantClient
import io.qdrant.client.QdrantGrpcClient
import io.qdrant.client.grpc.Collections._
import io.qdrant.client.grpc.Points._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}

@Singleton
class QdrantClientWrapper @Inject()(config: AppConfig)(implicit ec: ExecutionContext) {

  private val client: QdrantClient = new QdrantClient(
    QdrantGrpcClient.newBuilder(config.qdrant.host, config.qdrant.port, false).build()
  )

  private def toScalaFuture[A](lf: ListenableFuture[A]): Future[A] = {
    val promise = Promise[A]()
    Futures.addCallback(
      lf,
      new FutureCallback[A] {
        def onSuccess(result: A): Unit = promise.success(result)
        def onFailure(t: Throwable): Unit = promise.failure(t)
      },
      MoreExecutors.directExecutor()
    )
    promise.future
  }

  def ensureCollectionExists(): Future[Unit] =
    toScalaFuture(client.listCollectionsAsync()).flatMap { javaList =>
      val exists = javaList.asScala.contains(config.qdrant.collectionName)
      if (exists) Future.successful(())
      else
        toScalaFuture(client.createCollectionAsync(
          config.qdrant.collectionName,
          VectorParams.newBuilder()
            .setSize(config.qdrant.vectorDimension)
            .setDistance(Distance.Cosine)
            .build()
        )).map(_ => ())
    }

  def upsertPoint(
                   pointId: String,
                   vector: Seq[Float],
                   payload: Map[String, String]
                 ): Future[Unit] = {
    val payloadMap = payload.map { case (k, v) =>
      k -> io.qdrant.client.ValueFactory.value(v)
    }.asJava

    val point = PointStruct.newBuilder()
      .setId(io.qdrant.client.PointIdFactory.id(java.util.UUID.fromString(pointId)))
      .setVectors(io.qdrant.client.VectorsFactory.vectors(vector.map(Float.box).asJava))
      .putAllPayload(payloadMap)
      .build()

    toScalaFuture(
      client.upsertAsync(config.qdrant.collectionName, List(point).asJava)
    ).map(_ => ())
  }

  def search(
              queryVector: Seq[Float],
              limit: Int,
              scoreThreshold: Float,
              skillFilter: Option[List[String]] = None
            ): Future[List[ScoredPoint]] = {
    val builder = SearchPoints.newBuilder()
      .setCollectionName(config.qdrant.collectionName)
      .addAllVector(queryVector.map(Float.box).asJava)
      .setLimit(limit)
      .setScoreThreshold(scoreThreshold)
      .setWithPayload(io.qdrant.client.WithPayloadSelectorFactory.enable(true))

    skillFilter.foreach { skills =>
      if (skills.nonEmpty) {
        val condition = io.qdrant.client.grpc.Points.Filter.newBuilder()
          .addShould(
            io.qdrant.client.grpc.Points.Condition.newBuilder()
              .setField(
                io.qdrant.client.grpc.Points.FieldCondition.newBuilder()
                  .setKey("extracted_skills")
                  .setMatch(
                    io.qdrant.client.grpc.Points.Match.newBuilder()
                      .setKeywords(
                        io.qdrant.client.grpc.Points.RepeatedStrings.newBuilder()
                          .addAllStrings(skills.asJava)
                          .build()
                      )
                      .build()
                  )
                  .build()
              )
              .build()
          )
          .build()
        builder.setFilter(condition)
      }
    }

    toScalaFuture(client.searchAsync(builder.build())).map(_.asScala.toList)
  }

  def searchForNominee(
                        queryVector: Seq[Float],
                        nomineeId: String,
                        limit: Int,
                        scoreThreshold: Float
                      ): Future[List[ScoredPoint]] = {
    val nomineeFilter = io.qdrant.client.grpc.Points.Filter.newBuilder()
      .addMust(
        io.qdrant.client.grpc.Points.Condition.newBuilder()
          .setField(
            io.qdrant.client.grpc.Points.FieldCondition.newBuilder()
              .setKey("recipient_id")
              .setMatch(
                io.qdrant.client.grpc.Points.Match.newBuilder()
                  .setKeyword(nomineeId)
                  .build()
              )
              .build()
          )
          .build()
      )
      .build()

    val request = SearchPoints.newBuilder()
      .setCollectionName(config.qdrant.collectionName)
      .addAllVector(queryVector.map(Float.box).asJava)
      .setLimit(limit)
      .setScoreThreshold(scoreThreshold)
      .setWithPayload(io.qdrant.client.WithPayloadSelectorFactory.enable(true))
      .setFilter(nomineeFilter)
      .build()

    toScalaFuture(client.searchAsync(request)).map(_.asScala.toList)
  }

  def close(): Unit = client.close()
}