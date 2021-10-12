package apichallenge.server.redis

import apichallenge.client.routes.responses.RawBook
import apichallenge.server.models.{
  AuthorDateSearchParam,
  AuthorDateSearchResults
}
import cats.effect.IO
import io.circe._
import dev.profunktor.redis4cats.RedisCommands
import io.circe.generic.semiauto.deriveCodec

class AuthorBookRedisStore(
    var redis: RedisCommands[
      IO,
      AuthorDateSearchParam,
      AuthorDateSearchResults
    ]
) {

  def addSearchResults(
      author: String = "",
      date: List[String] = List.empty[String],
      books: List[RawBook] = List.empty[RawBook]
  ): IO[AuthorDateSearchResults] = {
    var params = AuthorDateSearchParam(author, date)
    var results = AuthorDateSearchResults(books)
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
  ): IO[Option[AuthorDateSearchResults]] = {
    return redis.get(AuthorDateSearchParam(author, date))
  }
}
