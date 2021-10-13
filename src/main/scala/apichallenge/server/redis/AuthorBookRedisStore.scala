package apichallenge.server.redis

import apichallenge.client.routes.responses.RawBook
import apichallenge.server.models.{
  AuthorDateSearchParam,
  Book,
  RawAuthorDateSearchResults
}

import cats.effect.IO
import dev.profunktor.redis4cats.RedisCommands

import scala.concurrent.duration._

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
    redis.expire(params, 3.minutes)
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
