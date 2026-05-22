package services

import models.{JwtPayload, User}
import repositories.{AppreciationRepository, InteractionEdgeRepository, UserRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Singleton
class SuggestionService @Inject()(userRepo: UserRepository, appreciationRepo: AppreciationRepository, interactionRepo: InteractionEdgeRepository)(implicit ec: ExecutionContext) {

  def getSuggestionsForUser(requester: JwtPayload, limit: Int = 10): Future[Either[String, Seq[User]]] = {

    val now = LocalDateTime.now()

    for {
      users <- userRepo.findByCompanyId(requester.companyId.get)
      edges <- interactionRepo.getEdgesForUser(requester.userId)
      myAppreciations <- appreciationRepo.getByGiver(requester.userId)

      candidateFutures = users
        .filter(_.id != requester.userId)
        .map { candidate =>
          for {
            candidateGiven <- appreciationRepo.getByGiver(candidate.id)
            candidateReceived <- appreciationRepo.getByReceiver(candidate.id)
          } yield {
            var score = 0.0

            val sameTeam = edges.exists(e =>
              e.interactionType == "same_team" &&
                e.userIdTo == candidate.id
            )
            if (sameTeam) score += 3.0

            val myIds = myAppreciations.map(_.receiverId).toSet
            val theirIds = candidateGiven.map(_.receiverId).toSet
            if (myIds.intersect(theirIds).nonEmpty)
              score += 2.0

            val recentReceived = candidateReceived.exists { app =>
              app.createdAt.exists(ct =>
                ChronoUnit.DAYS.between(ct, now) <= 30
              )
            }
            if (!recentReceived) score += 1.5

            val hasAppreciatedMe =
              candidateGiven.exists(_.receiverId == requester.userId)

            val iAppreciatedThem =
              myAppreciations.exists(_.receiverId == candidate.id)

            if (hasAppreciatedMe && !iAppreciatedThem)
              score += 1.0

            val recentlyAppreciated = myAppreciations.exists { app =>
              app.receiverId == candidate.id &&
                app.createdAt.exists(ct =>
                  ChronoUnit.DAYS.between(ct, now) <= 7
                )
            }
            if (recentlyAppreciated)
              score -= 2.0

            (candidate, score)
          }
        }

      suggestions <- Future.sequence(candidateFutures)

    } yield {
      Right(
        suggestions
          .sortBy(-_._2)
          .take(limit)
          .map(_._1)
      )
    }
  }
}