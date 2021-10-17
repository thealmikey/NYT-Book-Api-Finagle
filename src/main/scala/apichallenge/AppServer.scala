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
import cats.effect.IO.ioEffect._
import com.twitter.finagle.{Service, http, _}
import io.finch.catsEffect._
import com.twitter.finagle.http.Status.BadRequest
import com.twitter.finagle.http.service.HttpResponseClassifier
import com.twitter.finagle.http.{Request, Response, Version}
import com.twitter.finagle.oauth2._
import com.twitter.util.Future
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.effect.Log.NoOp.instance
import eu.timepit.refined.api.Refined
import io.circe.parser
import io.finch.oauth2.{authorize, issueAccessToken}
import io.finch.{
  Application,
  Bootstrap,
  Endpoint,
  EndpointModule,
  Input,
  InternalServerError,
  Ok,
  Output,
  Outputs,
  TooManyRequests,
  Trace,
  Unauthorized
}
import pureconfig.ConfigConvert.fromReaderAndWriter
import pureconfig.ConfigSource
import shapeless.HList.ListCompat.::

import scala.util.{Failure, Success}
import io.finch.catsEffect._

import scala.concurrent.ExecutionContext.Implicits.global
import io.finch.circe._
import io.circe._
import io.circe.generic.semiauto._
import io.circe.generic.auto._
import pureconfig.generic.auto._
import cats.syntax.all._
import io.circe.syntax._
import apichallenge.client.services.NyTimesService
import apichallenge.server.utils.util._
import apichallenge.server.utils.util._

import java.util.Date

object AppServer extends IOApp with EndpointModule[IO] {

  implicit val dateEncoder: Encoder[Date] =
    Encoder[Long].contramap[Date](d ⇒ d.getTime)
  implicit val dateDecoder: Decoder[Date] =
    Decoder.instance(a => a.as[Long].map(new Date(_)))

  def authInfoFn(
      redisDataHandler: DataHandler[UserHashed]
  ) =
    authorize[IO, UserHashed](redisDataHandler)

  def accessToken(
      redisDataHandler: DataHandler[UserHashed]
  ): Endpoint[IO, GrantResult] =
    issueAccessToken[IO, UserHashed](redisDataHandler)

  def tokensFn(
      redisDataHandler: RedisUserDataHandler
  ) =
    post("users" :: "auth" :: accessToken(redisDataHandler))

  def registerFn(
      redisDataHandler: RedisUserDataHandler
  ) =
    post("register" :: body[User, Application.Json]).mapOutputAsync { user =>
      IO.fromFuture {
        IO {
          redisDataHandler
            .storeHashedUser(user)
            .flatMap { userHashed =>
              redisDataHandler.createAccessToken(
                redisDataHandler.createAuthInfo(userHashed.get)
              )
            }
            .map(Ok(_))
            .asScala
        }
      }
    }

  def auth(
      compiled: Endpoint.Compiled[IO],
      redisDataHandler: RedisUserDataHandler
  ): Endpoint.Compiled[IO] = {
    var authService = authInfoFn(redisDataHandler).toService
    Endpoint.Compiled[IO] {
      case req => {

        if (
          req.uri.contains("auth") || req.uri.contains("login") || req.uri
            .contains("register")
        ) {
          compiled(req)
        } else {
          redisDataHandler.findTheAccessToken(
            req.wwwAuthenticate.getOrElse("")
          ) match {
            case Some(value) => {
              println(
                s"Got the code from the data handler,what we got in browser,${req.wwwAuthenticate.getOrElse("")}"
              )
              compiled(req)

            }
            case None => {
              println(
                s"DIDNT GET the code from the data handler,what we got in browser,${req.wwwAuthenticate
                  .getOrElse("")}"
              )
              IO.pure(Trace.empty -> Right(Response(http.Status.Unauthorized)))
            }
          }
        }
      }
      case _ => {
        println("Something else but we didn even check the backend")
        IO.pure(Trace.empty -> Right(Response(http.Status.Unauthorized)))
      }

    }
  }

//  def loginFn(redisDataHandler: RedisUserDataHandler) =
//    post("login" :: body[User, Application.Json]).map({ user =>
//     redisDataHandler.createHashedUser(user).flatMap{
//
//     }
//      issueAccessToken(redisDataHandler)
//    })

  val prod = None

  val config = ConfigSource.default.load[AppServerConf]

  val configOption = config.toOption
  val apiKey = configOption.map(_.nytApiKey.key).getOrElse("")
  println("The api key is", apiKey)
  val redisUrl: String =
    prod
      .flatMap(_ => configOption.map(_.redis.host))
      .getOrElse("redis://localhost")

  var userHashTokenStore =
    RedisJsonCache.createServer[UserHashed, AccessToken] _
  var clientUserHashStore = RedisJsonCache.createServer[ApiClient, UserHashed] _
  var apiStringStore = RedisJsonCache.createServer[String, AccessToken] _
  var tokenUserStore = RedisJsonCache.createServer[String, UserHashed] _
  var apiUserStore = RedisJsonCache.createServer[User, UserHashed] _

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

  val redisUserAuth = for {
    client <- RedisClient[IO].from(redisUrl)
    userHashToken <- userHashTokenStore(client)
    clientUserHash <- clientUserHashStore(client)
    apiString <- apiStringStore(client)
    tokenUser <- tokenUserStore(client)
    apiUser <- apiUserStore(client)
  } yield RedisUserDataHandler(
    userHashToken,
    clientUserHash,
    apiString,
    tokenUser,
    apiUser
  )

  val createApp = for {
    authorBookService <- authorBookServiceResource
    redisAuth <- redisUserAuth
    services <- Resource.liftF[IO, Service[Request, Response]](
      IO(
        Endpoint.toService(
          auth(
            Bootstrap
              .serve[Application.Json](
                registerFn(redisAuth) :+:
                  tokensFn(redisAuth) :+:
                  authorDateBookSearchEndpoint(authorBookService)
              )
              .compile,
            redisAuth
          )
        )
      )
    )
  } yield Http.server.withHttpStats
    .withResponseClassifier(Api.nytResponseClassifier)
    .withLabel("NYTimes-Http-Client")
    .serve(":8080", services)

  override def run(args: List[String]): IO[ExitCode] =
    createApp.use(_ => IO.never).as(ExitCode.Success)

  def authorDateBookSearchEndpoint(
      authorBookService: AuthorBookService
  ) = {
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
