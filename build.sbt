name := """pulse-backend"""
organization := "com.aron"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.18"
val circeVersion = "0.14.6"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test

libraryDependencies ++= Seq(
  "org.postgresql" % "postgresql" % "42.7.3",
  "com.typesafe.play" %% "play-slick" % "5.1.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "5.1.0"
)

libraryDependencies += "net.debasishg" %% "redisclient" % "3.42"
libraryDependencies += evolutions

libraryDependencies +="com.github.jwt-scala" %% "jwt-core" % "10.0.1"

libraryDependencies +="com.github.jwt-scala" %% "jwt-json-common" % "10.0.1"

libraryDependencies += "org.mindrot" % "jbcrypt" % "0.4"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core"     % circeVersion,
  "io.circe" %% "circe-generic"  % circeVersion,
  "io.circe" %% "circe-parser"   % circeVersion
)

libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always



// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.aron.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.aron.binders._"
