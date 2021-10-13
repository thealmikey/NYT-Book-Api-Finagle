package apichallenge.server.redis

import apichallenge.client.routes.responses.RawBook
import apichallenge.server.models.{
  AuthorDateSearchParam,
  Book,
  RawAuthorDateSearchResults
}
import apichallenge.server.utils.DateUtil.Date
import cats.effect.IO
import io.circe._
import dev.profunktor.redis4cats.RedisCommands
import eu.timepit.refined.api.Refined
import io.circe.generic.semiauto.deriveCodec

class AuthorBookRedisStore(
    var redis: RedisCommands[
      IO,
      AuthorDateSearchParam,
      RawAuthorDateSearchResults
    ]
) {

  def addSearchResults(
      author: String = "",
      date: List[String] = List.empty[String],
      books: List[RawBook] = List.empty[RawBook]
  ): IO[RawAuthorDateSearchResults] = {
    var params = AuthorDateSearchParam(author, date)
    var results = RawAuthorDateSearchResults(books)
    return redis
      .set(
        params,
        results
      )
      .as(results)
  }

  def fetchSearchResults(
      author: String = "",
      date: List[String] = List.empty[String]
  ): IO[Option[RawAuthorDateSearchResults]] = {
    return redis.get(AuthorDateSearchParam(author, date))
  }
}
