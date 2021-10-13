package apichallenge

import apichallenge.client.services.NyTimesService
import apichallenge.server.models.{
  AuthorDateSearchParam,
  RawAuthorDateSearchResults
}
import apichallenge.server.redis.{AuthorBookRedisStore, RedisJsonCache}
import apichallenge.server.services.AuthorBookService
import cats.effect.{ContextShift, ExitCode, IO, IOApp, Resource}
import cats.effect.IO.ioEffect
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.effect.Log.NoOp.instance
import io.finch.{
  Application,
  Bootstrap,
  EndpointModule,
  InternalServerError,
  Ok
}
//import io.finch._
import scala.concurrent.ExecutionContext.Implicits.global
import com.twitter.finagle.Http
import io.finch.circe._
import io.circe.generic.auto._

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
      authorBookService
        .searchBooksByAuthorAndDate(
          author.replaceAll("\\s", "_"),
          year.map(_.toString())
        )
        .map(Ok)
    }.handle {
      case e: Exception => InternalServerError(e)
    }
  }
}
