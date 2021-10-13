package apichallenge.client.services

import apichallenge.client.routes.NyTimes.apiKey
import apichallenge.client.routes.responses.{RawBook, RawBookResponse}
import cats.data.EitherT
import com.twitter.finagle.{Http, Service, ServiceFactory}
import com.twitter.finagle.http.{Method, Request, RequestBuilder, Response}
import com.twitter.logging.Logger
import io.circe.generic.auto._
import io.circe.parser.parse
import apichallenge.server.utils.util._
import cats.implicits._
import cats.data._
import cats.effect.{ContextShift, IO}
import cats.syntax.all._
import apichallenge.client.utils._
import apichallenge.server.utils.ApiExceptions.{
  ApiAuthorizationException,
  ApiException,
  AppGenericException,
  DisconnectionException,
  RateLimitException
}
//import cats.Monad
//import cats.implicits.toBifunctorOps
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class NyTimesService(
    val client: Service[Request, Response] = Http
      .newService("api.nytimes.com:80")
) {
  implicit val ec = ExecutionContext.global
  implicit val contextShiftIO: ContextShift[IO] = IO.contextShift(global)
//  implicit val monad: Monad[Future] = cats.implicits.catsStdInstancesForFuture

  val log = Logger.get(getClass)

  def searchBooksByAuthorName(
      authorName: String,
      offset: Int = 0
  ): IO[Either[ApiException, (Int, Option[List[RawBook]])]] = {
    IO.fromFuture {
      IO {
        val allBooksUrl = s"/svc/books/v3/lists/best-sellers/history.json"
        val paramStr =
          Map("api-key" -> apiKey, "author" -> authorName, "offset" -> offset)
            .map {
              case (k, v) =>
                k + '=' + v
            } mkString ("?", "&", "")

        val allBooksRequest = RequestBuilder.safeBuildGet(
          RequestBuilder
            .create()
            .url(
              s"https://api.nytimes.com$allBooksUrl" + paramStr
            )
        )

        //    Request(Method.Get, s"/$allBooksUrl?api-key=${apiKey}")
        allBooksRequest.contentType = "application/json"
        allBooksRequest.accept = "application/json"

        (for {
          response <- EitherT.right(client(allBooksRequest).asScala(ec))
          rawJson <-
            EitherT
              .fromEither[Future](parse(response.getContentString()))
              .map { x =>
                println(s"The value is $x")
                x
              }
              .leftMap(failure => {
                log.error("full message", failure.printStackTrace())
                log.error(failure, "error parsing JSON to string")
                AppGenericException("Error converting JSON proper")
                //                (0, Option(List.empty[RawBook]))
              })
          booksRes <-
            EitherT
              .fromEither[Future](rawJson.as[RawBookResponse])
              .map { x =>
                println(s"The value is trying to parse json $x")
                x
              }
              .map(booksRes =>
                (booksRes.num_results.getOrElse(0), booksRes.results)
              )
              .leftMap(failure => {
                log.error(failure, "error parsing JSON into BooksResponse")
                AppGenericException("error parsing JSON into BooksResponse")
                //                throw mapExceptions(response)
                //                (0, Option(List.empty[RawBook]))
              })

        } yield booksRes).value
      }
    }
  }

}
