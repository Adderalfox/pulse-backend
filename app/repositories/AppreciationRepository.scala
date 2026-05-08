package repositories

import javax.inject._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.PostgresProfile
import models.Appreciation

import java.util.UUID

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AppreciationRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {
  private val dbConfig = dbConfigProvider.get[PostgresProfile]

  import dbConfig.profile.api._

  private class AppreciationTable(tag: Tag) extends Table[Appreciation](tag, "appreciations") {
    def id = column[String]("id", O.PrimaryKey)

    def giverId = column[String]("giver_id")

    def receiverId = column[String]("receiver_id")

    def companyId = column[String]("company_id")

    def departmentId = column[Option[String]]("department_id")

    def text = column[String]("text")

    def appreciationType = column[String]("appreciation_type")

    def visibility = column[String]("visibility")

    def pointsAwarded = column[Int]("points_awarded")

    def createdAt = column[java.time.LocalDateTime]("created_at")

    def updatedAt = column[java.time.LocalDateTime]("updated_at")

    def * = (id, giverId, receiverId, companyId, departmentId, text, appreciationType, visibility, pointsAwarded, createdAt.?, updatedAt.?) <> ((Appreciation.apply _).tupled, Appreciation.unapply)

    def ins = (id, giverId, receiverId, companyId, departmentId, text, appreciationType, visibility, pointsAwarded)
  }

  private val appreciations = TableQuery[AppreciationTable]

  def create(app: Appreciation): Future[Appreciation] = {
    val appWithId =
      if (app.id.nonEmpty) app
      else app.copy(id = UUID.randomUUID().toString)

    val insertAction = appreciations.map(_.ins) += (
      appWithId.id,
      appWithId.giverId,
      appWithId.receiverId,
      appWithId.companyId,
      appWithId.departmentId,
      appWithId.text,
      appWithId.appreciationType,
      appWithId.visibility,
      appWithId.pointsAwarded
    )

    dbConfig.db.run(insertAction).map(_ => appWithId)
  }

  def findById(id: String): Future[Option[Appreciation]] =
    dbConfig.db.run(appreciations.filter(_.id === id).result.headOption)


  def getByReceiver(receiverId: String): Future[Seq[Appreciation]] = {
    val query = appreciations
      .filter(_.receiverId === receiverId)
      .sortBy(_.createdAt.desc)
      .map { a =>
        (a.id, a.giverId, a.receiverId, a.companyId, a.departmentId, a.text,
          a.appreciationType, a.visibility, a.pointsAwarded,
          a.createdAt.asColumnOf[java.time.LocalDateTime],
          a.updatedAt.asColumnOf[java.time.LocalDateTime])
      }

    dbConfig.db.run(query.result).map { rows =>
      rows.map { case (id, gId, rId, cId, dId, txt, aType, vis, pts, ct, ut) =>
        Appreciation(id, gId, rId, cId, dId, txt, aType, vis, pts, Some(ct), Some(ut))
      }
    }
  }
//    dbConfig.db.run(
//      appreciations
//        .filter(_.receiverId === receiverId)
//        .sortBy(_.createdAt.desc)
//        .result
//    )

  def getByGiver(giverId: String): Future[Seq[Appreciation]] = {
    val query = appreciations
      .filter(_.giverId === giverId)
      .sortBy(_.createdAt.desc)
      .map { a =>
        (a.id, a.giverId, a.receiverId, a.companyId, a.departmentId, a.text,
          a.appreciationType, a.visibility, a.pointsAwarded,
          a.createdAt.asColumnOf[java.time.LocalDateTime],
          a.updatedAt.asColumnOf[java.time.LocalDateTime])
      }

    dbConfig.db.run(query.result).map { rows =>
      rows.map { case (id, gId, rId, cId, dId, txt, aType, vis, pts, ct, ut) =>
        Appreciation(id, gId, rId, cId, dId, txt, aType, vis, pts, Some(ct), Some(ut))
      }
    }
  }
//    dbConfig.db.run(
//      appreciations
//        .filter(_.giverId === giverId)
//        .sortBy(_.createdAt.desc)
//        .result
//    )



  def getRawFeedCandidates(companyId: String, limit: Int, offset: Int): Future[Seq[Appreciation]] = {
    val query = appreciations
      .filter(_.companyId === companyId)
      .sortBy(_.createdAt.desc)
      .drop(offset)
      .take(limit)
      .map { a =>
        (a.id, a.giverId, a.receiverId, a.companyId, a.departmentId, a.text,
          a.appreciationType, a.visibility, a.pointsAwarded,
          a.createdAt.asColumnOf[java.time.LocalDateTime],
          a.updatedAt.asColumnOf[java.time.LocalDateTime])
      }

    dbConfig.db.run(query.result).map { rows =>
      rows.map { case (id, gId, rId, cId, dId, txt, aType, vis, pts, ct, ut) =>
        Appreciation(id, gId, rId, cId, dId, txt, aType, vis, pts, Some(ct), Some(ut))
      }
    }
  }

  def countForUser(userId: String): Future[Int] =
    dbConfig.db.run(
      appreciations
        .filter(_.receiverId === userId)
        .length
        .result
    )
}