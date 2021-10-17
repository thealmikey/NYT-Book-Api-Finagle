import sbt._

object Dependencies {

  object Versions {
    val CatsEffect = "2.1.0"
    val CatsRetry = "1.1.1"
    val Circe = "0.13.0"
    val KindProjector = "0.11.0"
    val finagle = "20.1.0"
    val finch = "0.32.1"
    val Redis4Cats = "0.10.3"
    val Refined = "0.9.17"
    val JodaTime = "2.10.12"
    val CatBird = "21.8.0"
    val Bijection = "0.9.7"
    val Shapeless = "2.3.3"
    val Logback = "1.2.3"
    val Zio = "2.0.0-M2+139-8caf95dd-SNAPSHOT"
    val FastParse = "2.2.2"
    val PureConfig = "0.17.0"
    val FinagleOauth = "19.8.0"
    val TaglessSecurity = "0.0.1-M11"
    val FinchOauth = "0.28.0"
  }

  object Libs {
    /*
     * Easily create dependencies with similar naming patter
     * */
    def circe(artifact: String): ModuleID =
      "io.circe" %% s"circe-$artifact" % Versions.Circe

    val CirceCore = circe("core")
    val CirceGeneric = circe("generic")
    val CirceOptics = circe("optics")
    val CirceParser = circe("parser")
    val CirceRefined = circe("refined")
    val CirceGenericExtras = circe("generic-extras")

    def finch(artifact: String): ModuleID =
      "com.github.finagle" %% s"finchx-$artifact" % Versions.finch

    val FinchCore = finch("core")
    val FinchGeneric = finch("generic")
    val FinchCirce = finch("circe")
    val FinchTest = finch("test")

    // Http
    val Finagle = "com.twitter" %% "finagle-http" % Versions.finagle

    //Caching
    val Redis4Cats =
      "dev.profunktor" %% "redis4cats-effects" % Versions.Redis4Cats

    val Refined = "eu.timepit" %% "refined" % Versions.Refined
    val RefinedCats = "eu.timepit" %% "refined-cats" % Versions.Refined

    val CatsEffect = "org.typelevel" %% "cats-effect" % Versions.CatsEffect
    val CatsRetry = "com.github.cb372" %% "cats-retry" % Versions.CatsRetry

    // https://mvnrepository.com/artifact/joda-time/joda-time
    val JodaTime = "joda-time" % "joda-time" % Versions.JodaTime

    val CatBird = "io.catbird" %% "catbird-finagle" % Versions.CatBird
    // https://mvnrepository.com/artifact/com.twitter/bijection-core
    val Bijection = "com.twitter" %% "bijection-core" % Versions.Bijection
    val Shapeless = "com.chuusai" %% "shapeless" % Versions.Shapeless

    val Logback = "ch.qos.logback" % "logback-classic" % Versions.Logback

    val Zio = "dev.zio" %% "zio" % Versions.Zio

    val FastParse = "com.lihaoyi" %% "fastparse" % Versions.FastParse

    val PureConfig =
      "com.github.pureconfig" %% "pureconfig" % Versions.PureConfig

    val FinagleOauth =
      "com.github.finagle" %% "finagle-oauth2" % Versions.FinagleOauth

    val FinchOauth =
      "com.github.finagle" %% "finchx-oauth2" % Versions.FinchOauth

    val TaglessSecurityCommon =
      "io.github.jmcardon" %% "tsec-common" % Versions.TaglessSecurity
    val TaglessSecurityPassword =
      "io.github.jmcardon" %% "tsec-password" % Versions.TaglessSecurity
  }

}
