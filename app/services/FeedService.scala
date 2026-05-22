package services

import models.{AppreciationWithDetails, JwtPayload, Role}
import repositories.{AppreciationRepository, AppreciationSkillTagRepository, InteractionEdgeRepository, UserRepository}
import javax.inject._
import concurrent.{ExecutionContext, Future}


@Singleton
class FeedService @Inject()(appreciationRepo: AppreciationRepository, skillTagRepo: AppreciationSkillTagRepository, interactionEdgeRepo: InteractionEdgeRepository, userRepo: UserRepository)(implicit ec: ExecutionContext) {

  def getFeed(requester: JwtPayload, limit: Int = 20, offset: Int = 0): Future[Either[String, Seq[AppreciationWithDetails]]] = {
    for {
      paginated <- appreciationRepo.getRawFeedCandidates(requester.companyId.get, limit, offset)
      results <- Future.sequence(
        paginated.map { app =>
          for {
            giver <- userRepo.findById(app.giverId)
            receiver <- userRepo.findById(app.receiverId)
            tags <- skillTagRepo.getByAppreciation(app.id)
          } yield {
            AppreciationWithDetails(
              app,
              giver.map(_.name).getOrElse("Unknown"),
              giver.map(_.role).getOrElse(Role.EMPLOYEE),
              receiver.map(_.name).getOrElse("Unknown"),
              receiver.map(_.role)getOrElse(Role.EMPLOYEE),
              tags
            )
          }
        }
      )
    } yield Right(results)
  }
}