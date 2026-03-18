//package controllers
//
//import javax.inject._
//import play.api.mvc._
//import repositories.UserRepository
//import scala.concurrent.ExecutionContext
//
//@Singleton
//class UserController @Inject()(cc: ControllerComponents, repo: UserRepository)(implicit ec:ExecutionContext) extends AbstractController(cc) {
//  def createUser = Action.async {request =>
//    repo.create("Happy", "achetia@gmail.com", "employee")
//      .map(_=> Ok("User created"))
//  }
//}