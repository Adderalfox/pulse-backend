package modules

import com.google.inject.AbstractModule

class AppModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[utils.AppConfig]).asEagerSingleton()
    bind(classOf[utils.GeminiClient]).asEagerSingleton()
    bind(classOf[utils.QdrantClientWrapper]).asEagerSingleton()
    bind(classOf[startup.QdrantStartup]).asEagerSingleton()
    bind(classOf[repositories.NominationDraftRepository]).asEagerSingleton()
    bind(classOf[services.NominationService]).asEagerSingleton()
  }
}