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

  private def ensureCollection(collectionName: String, vectorDimension: Int): Future[Unit] =
    toScalaFuture(client.listCollectionsAsync()).flatMap { javaList =>
      val exists = javaList.asScala.contains(collectionName)
      if (exists) Future.successful(())
      else
        toScalaFuture(
          client.createCollectionAsync(
            collectionName,
            VectorParams.newBuilder()
              .setSize(vectorDimension)
              .setDistance(Distance.Cosine)
              .build()
          )
        ).map(_ => ())
    }

  def ensureCollectionExists(): Future[Unit] =
    ensureCollection(config.qdrant.collectionName, config.qdrant.vectorDimension)

  def ensureAwardDefinitionsCollectionExists(): Future[Unit] =
    ensureCollection(config.qdrant.awardDefinitionsCollectionName, config.qdrant.vectorDimension)

  def upsertPoint(
                   pointId: String,
                   vector:  Seq[Float],
                   payload: Map[String, String]
                 ): Future[Unit] = upsertToCollection(config.qdrant.collectionName, pointId, vector, payload)

  def upsertAwardDefinition(
                             pointId: String,
                             vector:  Seq[Float],
                             payload: Map[String, String]
                           ): Future[Unit] = upsertToCollection(config.qdrant.awardDefinitionsCollectionName, pointId, vector, payload)

  private def upsertToCollection(
                                  collectionName: String,
                                  pointId:        String,
                                  vector:         Seq[Float],
                                  payload:        Map[String, String]
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
      client.upsertAsync(collectionName, List(point).asJava)
    ).map(_ => ())
  }

  def search(
              queryVector:    Seq[Float],
              limit:          Int,
              scoreThreshold: Float,
              skillFilter:    Option[List[String]] = None
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
                        queryVector:    Seq[Float],
                        nomineeId:      String,
                        limit:          Int,
                        scoreThreshold: Float
                      ): Future[List[ScoredPoint]] = {
    val nomineeFilter = buildMustMatchFilter("recipient_id", nomineeId)

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

  def searchAwardDefinitions(
                              profileVector: Seq[Float],
                              companyId:     String,
                              departmentId:  String,
                              limit:         Int
                            ): Future[List[ScoredPoint]] = {

    // Must: company_id matches
    val companyCondition = io.qdrant.client.grpc.Points.Condition.newBuilder()
      .setField(
        io.qdrant.client.grpc.Points.FieldCondition.newBuilder()
          .setKey("company_id")
          .setMatch(
            io.qdrant.client.grpc.Points.Match.newBuilder()
              .setKeyword(companyId)
              .build()
          )
          .build()
      )
      .build()

    val deptCondition = io.qdrant.client.grpc.Points.Condition.newBuilder()
      .setField(
        io.qdrant.client.grpc.Points.FieldCondition.newBuilder()
          .setKey("department_id")
          .setMatch(
            io.qdrant.client.grpc.Points.Match.newBuilder()
              .setKeyword(departmentId)
              .build()
          )
          .build()
      )
      .build()

    val companyWideCondition = io.qdrant.client.grpc.Points.Condition.newBuilder()
      .setField(
        io.qdrant.client.grpc.Points.FieldCondition.newBuilder()
          .setKey("department_id")
          .setMatch(
            io.qdrant.client.grpc.Points.Match.newBuilder()
              .setKeyword("")
              .build()
          )
          .build()
      )
      .build()

    val combinedFilter = io.qdrant.client.grpc.Points.Filter.newBuilder()
      .addMust(companyCondition)
      .addShould(deptCondition)
      .addShould(companyWideCondition)
      .build()

    val request = SearchPoints.newBuilder()
      .setCollectionName(config.qdrant.awardDefinitionsCollectionName)
      .addAllVector(profileVector.map(Float.box).asJava)
      .setLimit(limit)
      .setWithPayload(io.qdrant.client.WithPayloadSelectorFactory.enable(true))
      .setFilter(combinedFilter)
      .build()

    toScalaFuture(client.searchAsync(request)).map(_.asScala.toList)
  }


  private def buildMustMatchFilter(key: String, value: String): io.qdrant.client.grpc.Points.Filter =
    io.qdrant.client.grpc.Points.Filter.newBuilder()
      .addMust(
        io.qdrant.client.grpc.Points.Condition.newBuilder()
          .setField(
            io.qdrant.client.grpc.Points.FieldCondition.newBuilder()
              .setKey(key)
              .setMatch(
                io.qdrant.client.grpc.Points.Match.newBuilder()
                  .setKeyword(value)
                  .build()
              )
              .build()
          )
          .build()
      )
      .build()

  def close(): Unit = client.close()
}