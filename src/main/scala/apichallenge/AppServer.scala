package apichallenge

import apichallenge.client.services.NyTimesService
import apichallenge.server.models.{
  AuthorDateSearchParam,
  AuthorDateSearchResults
}
import apichallenge.server.redis.{AuthorBookRedisStore, RedisJsonCache}
import apichallenge.server.services.AuthorBookService
import cats.effect.{ContextShift, ExitCode, IO, IOApp, Resource}
import cats.effect.IO.ioEffect
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.effect.Log.NoOp.instance
import io.finch._

import scala.concurrent.ExecutionContext.Implicits.global
import com.twitter.finagle.Http
import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.Decoder.Result
import io.finch.circe._
import io.circe.generic.auto._
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

object AppServer extends IOApp with EndpointModule[IO] {

  val formatter = DateTimeFormat.forPattern("yyyy-MM-dd")

//  implicit val JodaTimeCodec: Encoder[DateTime] with Decoder[DateTime] =
//    new Encoder[DateTime] with Decoder[DateTime] {
//      override def apply(a: DateTime): Json =
//        Encoder.encodeString.apply(formatter.print(a))
//
//      override def apply(c: HCursor): Result[DateTime] =
//        Decoder.decodeString.map(s => formatter.parseDateTime(s)).apply(c)
//    }

  implicit val contextShiftIO: ContextShift[IO] = IO.contextShift(global)

  val authorBookServiceResource =
    for {
      client <- RedisClient[IO].from("redis://localhost")
      redis <-
        RedisJsonCache
          .createServer[AuthorDateSearchParam, AuthorDateSearchResults](client)

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

  def authorDateBookSearchEndpoint(authorBookService: AuthorBookService) = {
    get(
      "me" :: "books" :: "list" :: param[String]("author") :: params[String](
        "year"
      )
    ) { (author: String, year: List[String]) =>
      authorBookService
        .searchApiBooksByAuthorAndDate(author.replaceAll("\\s", "_"), year)
        .map(Ok)
    }
  }

  override def run(args: List[String]): IO[ExitCode] =
    createApp.use(_ => IO.never).as(ExitCode.Success)

}
