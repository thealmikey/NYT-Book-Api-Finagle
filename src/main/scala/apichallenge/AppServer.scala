package apichallenge

import apichallenge.client.routes.responses.NyTimesErrorResponse
import apichallenge.client.utils.Api
import apichallenge.server.models.{
  AuthorDateSearchParam,
  RawAuthorDateSearchResults,
  User
}
import apichallenge.server.redis.{AuthorBookRedisStore, RedisJsonCache}
import apichallenge.server.routes.AuthorDateBookSearchValidation
import apichallenge.server.services.{AuthorBookService, RedisUserDataHandler}
import apichallenge.server.utils.ApiExceptions.{
  ApiAuthorizationException,
  ApiException,
  AppGenericException,
  RateLimitException
}
import apichallenge.shared.config.AppServerConf
import cats.effect.{ContextShift, ExitCode, IO, IOApp, Resource}
import cats.effect.IO.ioEffect
import com.twitter.finagle.OAuth2.{authorize, issueAccessToken}
import com.twitter.finagle.Service
import com.twitter.finagle.http.Status.BadRequest
import com.twitter.finagle.http.service.HttpResponseClassifier
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.oauth2.{AuthInfo, GrantResult}
import com.twitter.util.Future
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.effect.Log.NoOp.instance
import eu.timepit.refined.api.Refined
import io.circe.parser
import io.finch.{
  Application,
  Bootstrap,
  Endpoint,
  EndpointModule,
  InternalServerError,
  Ok,
  Outputs,
  TooManyRequests,
  Unauthorized
}
import pureconfig.ConfigConvert.fromReaderAndWriter
import pureconfig.ConfigSource
import shapeless.HList.ListCompat.::

import scala.util.{Failure, Success}
//import io.finch._
import scala.concurrent.ExecutionContext.Implicits.global
import com.twitter.finagle._
import io.finch.circe._
import io.circe.generic.auto._
import eu.timepit.refined._
import eu.timepit.refined.auto._
import eu.timepit.refined.generic._
import eu.timepit.refined.string._
import pureconfig.generic.auto._
import apichallenge.client.services.NyTimesService
import apichallenge.server.utils.util._
import apichallenge.server.utils.util._

object AppServer extends IOApp with EndpointModule[IO] {
//  def authInfo(
//      redisDataHandler: RedisUserDataHandler
//  ) =
//    authorize[User](redisDataHandler)

//  def accessToken(
//      redisDataHandler: RedisUserDataHandler
//  ): Endpoint[IO, GrantResult] =
//    issueAccessToken[User](redisDataHandler)

  val prod = None

  val config = ConfigSource.default.load[AppServerConf]

  val configOption = config.toOption
  val apiKey = configOption.map(_.nytApiKey.key).getOrElse("")
  println("The api key is", apiKey)
  val redisUrl: String =
    prod
      .flatMap(_ => configOption.map(_.redis.host))
      .getOrElse("redis://localhost")

  implicit val contextShiftIO: ContextShift[IO] = IO.contextShift(global)

  val authorBookServiceResource =
    for {
      client <- RedisClient[IO].from(redisUrl)
      redisForBooks <-
        RedisJsonCache
          .createServer[AuthorDateSearchParam, RawAuthorDateSearchResults](
            client
          )
      redisForUsers <-
        RedisJsonCache
          .createServer[User, GrantResult](
            client
          )

    } yield new AuthorBookService(
      new AuthorBookRedisStore(redisForBooks),
      new NyTimesService(apiKey = apiKey)
    )

  val createApp = for {
    authorBookService <- authorBookServiceResource
    services <- Resource.liftF[IO, Service[Request, Response]](
      IO(
        Bootstrap
          .serve[Application.Json](
            authorDateBookSearchEndpoint(authorBookService)
          )
          .toService
      )
    )
  } yield Http.server.withHttpStats
    .withResponseClassifier(Api.nytResponseClassifier)
    .withLabel("NYTimes-Http-Client")
    .serve(":8080", services)

  override def run(args: List[String]): IO[ExitCode] =
    createApp.use(_ => IO.never).as(ExitCode.Success)

  def authorDateBookSearchEndpoint(authorBookService: AuthorBookService) = {
    get(
      "me" :: "books" :: "list" :: param[String]("author") :: params[String](
        "year"
      )
    ) { (author: String, year: List[String]) =>
      var checkedYears = year.takeWhile {
        AuthorDateBookSearchValidation.validate(_) match {
          case Success(value) => true
          case Failure(exception) => {
            throw AppGenericException(
              exception.getMessage,
              new Throwable(exception.getMessage)
            )
            false
          }
        }
      }

      authorBookService
        .searchBooksByAuthorAndDate(
          author.replaceAll("\\s", "_"),
          year.map(_.toString())
        )
        .map { apiResult =>
          apiResult match {
            case Right(value) => value
          }
        }
        .map(Ok)
    }.handle {
      case e: ApiException => {
//        Unauthorized(e.asInstanceOf[Exception])
        e match {
          case RateLimitException(message, _, _) =>
            TooManyRequests(AppGenericException.asInstanceOf[Exception])
          case ApiAuthorizationException(message, _, _) =>
            Unauthorized(AppGenericException.asInstanceOf[Exception])

        }

      }
      case e: Exception => {
        println(s"only gets here with message,${e.getMessage}")
        InternalServerError((e))
      }
    }
  }
//  def handleApiException(apiException: ApiException) = {
//    apiException
//  }

}
