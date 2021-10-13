package apichallenge.server.models

import apichallenge.client.routes.responses.RawBook
import io.finch.circe._
import io.circe.generic.auto._

case class RawAuthorDateSearchResults(
    books: List[RawBook] = List.empty[RawBook]
)
