package apichallenge.client.services

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
import apichallenge.server.utils.ApiExceptions.{
  ApiException,
  AppGenericException
}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class NyTimesService(
    val client: Service[Request, Response] = Http
      .newService("api.nytimes.com:80"),
    val apiKey: String = ""
) {

  implicit val ec = ExecutionContext.global
  implicit val contextShiftIO: ContextShift[IO] = IO.contextShift(global)

  val log = Logger.get(getClass)
  /*
This method uses Finagle Client to get data from the API.
We model the return as an IO[Either[ApiException,(Int,Option[List[RawBook]])]

We model using IO to easily compose with other parts of the system in this case Redis.
IO also enables us to easily lift values without worrying about order of events and
synchronity issues. No need for Awaits with finite durations all over the place.

We model using Either as our request can fail or an operation after fetching, such as Deserializing
data can fail.
We model the data from the API using Option, this shows that we might succeed at hitting the endpoint
with request for an author book but the author might not be present in the list for some reason.
We can safely deserialize the absence of data with None and Circe would shape it to a list with a little
magic
   */
  def searchBooksByAuthorName(
      authorName: String,
      offset: Int = 0
  ): IO[Either[ApiException, (Int, Option[List[RawBook]])]] = {
    IO.fromFuture {
      IO {
        val allBooksUrl = s"/svc/books/v3/lists/best-sellers/history.json"
        val paramStr =
          Map(
            "api-key" -> apiKey,
            "author" -> authorName,
            "offset" -> offset
          ).map {
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
