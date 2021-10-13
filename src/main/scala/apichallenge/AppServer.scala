package apichallenge

import apichallenge.client.services.NyTimesService
import apichallenge.server.models.{
  AuthorDateSearchParam,
  RawAuthorDateSearchResults
}
import apichallenge.server.redis.{AuthorBookRedisStore, RedisJsonCache}
import apichallenge.server.routes.AuthorDateBookSearchValidation
import apichallenge.server.services.AuthorBookService
import apichallenge.server.utils.ApiExceptions.{
  ApiAuthorizationException,
  ApiException,
  DisconnectionException,
  GenericApiException,
  IllegalParamsException,
  JsonConvException,
  MissingQueryException,
  RateLimitException
}
import apichallenge.server.utils.DateUtil.Date
import apichallenge.server.utils.DateUtil.Date._
import cats.effect.{ContextShift, ExitCode, IO, IOApp, Resource}
import cats.effect.IO.ioEffect
import com.twitter.finagle.Service
import com.twitter.finagle.http.Status.BadRequest
import com.twitter.finagle.http.{Request, Response}
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.effect.Log.NoOp.instance
import eu.timepit.refined.api.Refined
import io.finch.{
  Application,
  Bootstrap,
  EndpointModule,
  InternalServerError,
  Ok
}

import scala.util.{Failure, Success}
//import io.finch._
import scala.concurrent.ExecutionContext.Implicits.global
import com.twitter.finagle.Http
import io.finch.circe._
import io.circe.generic.auto._
import eu.timepit.refined._
import eu.timepit.refined.auto._
import eu.timepit.refined.generic._
import eu.timepit.refined.string._

object AppServer extends IOApp with EndpointModule[IO] {

  implicit val contextShiftIO: ContextShift[IO] = IO.contextShift(global)

  val authorBookServiceResource =
    for {
      client <- RedisClient[IO].from("redis://localhost")
      redis <-
        RedisJsonCache
          .createServer[AuthorDateSearchParam, RawAuthorDateSearchResults](
            client
          )

    } yield new AuthorBookService(
      new AuthorBookRedisStore(redis),
      new NyTimesService()
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
  } yield Http.serve(":8080", services)

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
            throw IllegalParamsException(exception.getMessage)
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
            case Left(value)  => throw value
            case Right(value) => value
          }
        }
        .map(Ok)
    }.handle {
      case e: ApiException => throw handleApiException(e)
      case e: Exception => {
        println("only gets here")
        InternalServerError((e))
      }
    }
  }
  def handleApiException(apiException: ApiException) = {
    apiException match {
      case RateLimitException(message)    => RateLimitException(message)
      case MissingQueryException(message) => MissingQueryException(message)
      case DisconnectionException(message) =>
        DisconnectionException(message)
      case IllegalParamsException(message) => IllegalParamsException(message)
      case ApiAuthorizationException(message) =>
        ApiAuthorizationException(message)
      case GenericApiException(message) => GenericApiException(message)
      case JsonConvException(message)   => JsonConvException(message)
    }
  }
}
