import Dependencies._

name := "ApiChallenge"

version := "0.1"

resolvers += Resolver.sonatypeRepo("snapshots")

val root = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging, AshScriptPlugin, DockerPlugin)
  .settings(
    name := "ApiChallenge",
    version := "0.1.0-SNAPSHOT",
    description := "API server with Books and Authors information from NY-Times",
    scalaVersion := "2.12.8"
  )
libraryDependencies ++= Seq(
  Libs.Finagle,
  Libs.FinchCore,
  Libs.FinchCirce,
  Libs.FinchGeneric,
  Libs.CirceCore,
  Libs.CirceGeneric,
  Libs.CirceParser,
  Libs.CirceOptics,
  Libs.Redis4Cats,
  Libs.Refined,
  Libs.RefinedCats,
  Libs.CatsEffect,
  Libs.JodaTime,
  Libs.Shapeless,
  Libs.FastParse,
  Libs.CirceRefined,
  Libs.CirceGenericExtras
)
dockerBaseImage := "openjdk:jre-alpine"
dockerRepository := Some("thealmikey")
dockerExposedPorts.in(Docker) := Seq(8080)
daemonUser.in(Docker) := "root"
mainClass in Compile := Some("apichallenge.AppServer")
version.in(Docker) := "0.0.4"
