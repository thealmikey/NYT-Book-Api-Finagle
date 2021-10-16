package apichallenge

import apichallenge.client.routes.responses.NyTimesErrorResponse
import apichallenge.client.utils.Api
import apichallenge.server.models.{
  ApiClient,
  AuthorDateSearchParam,
  RawAuthorDateSearchResults,
  User,
  UserHashed
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
import com.twitter.finagle.oauth2.{AccessToken, AuthInfo, GrantResult}
import com.twitter.util.Future
import dev.profunktor.redis4cats.RedisCommands
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

import pureconfig.generic.auto._
import apichallenge.client.services.NyTimesService
import apichallenge.server.utils.util._
import apichallenge.server.utils.util._

object AppServer extends IOApp with EndpointModule[IO] {
//  def authInfoFn(
//      redisDataHandler: RedisUserDataHandler
//  ) =
//    authorize[AuthInfo[UserHashed]](redisDataHandler)
//
//  def accessToken(
//      redisDataHandler: RedisUserDataHandler
//  ): Endpoint[IO, GrantResult] =
//    issueAccessToken[UserHashed](redisDataHandler)
//
//  def tokensFn(
//      redisDataHandler: RedisUserDataHandler
//  ): Endpoint[IO, GrantResult] =
//    post("users" :: "auth" :: accessToken(redisDataHandler))

  val prod = None

  val config = ConfigSource.default.load[AppServerConf]

  val configOption = config.toOption
  val apiKey = configOption.map(_.nytApiKey.key).getOrElse("")
  println("The api key is", apiKey)
  val redisUrl: String =
    prod
      .flatMap(_ => configOption.map(_.redis.host))
      .getOrElse("redis://localhost")

//  var userHashTokenStore =
//    RedisJsonCache.createServer[UserHashed, AccessToken] _
//  var clientUserHashStore = RedisJsonCache.createServer[ApiClient, UserHashed] _
//  var apiStringStore = RedisJsonCache.createServer[String, AccessToken] _
//  var tokenUserStore = RedisJsonCache.createServer[String, UserHashed] _
//  var apiUserStore = RedisJsonCache.createServer[User, UserHashed] _

  implicit val contextShiftIO: ContextShift[IO] = IO.contextShift(global)

  val authorBookServiceResource =
    for {
      client <- RedisClient[IO].from(redisUrl)
      redisForBooks <-
        RedisJsonCache
          .createServer[AuthorDateSearchParam, RawAuthorDateSearchResults](
            client
          )

    } yield new AuthorBookService(
      new AuthorBookRedisStore(redisForBooks),
      new NyTimesService(apiKey = apiKey)
    )

//  val redisUserAuth = for {
//    client <- RedisClient[IO].from(redisUrl)
//    userHashToken <- userHashTokenStore(client)
//    clientUserHash <- clientUserHashStore(client)
//    apiString <- apiStringStore(client)
//    tokenUser <- tokenUserStore(client)
//    apiUser <- apiUserStore(client)
//  } yield RedisUserDataHandler(
//    userHashToken,
//    clientUserHash,
//    apiString,
//    tokenUser,
//    apiUser
//  )

  val createApp = for {
    authorBookService <- authorBookServiceResource
//    redisAuth <- redisUserAuth
    services <- Resource.liftF[IO, Service[Request, Response]](
      IO(
        Bootstrap
          .serve[Application.Json](
//            tokensFn(redisAuth) :+:
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
