package apichallenge.server.models

import io.finch.circe._
import io.circe.generic.auto._

case class AuthorDateSearchParam(
    author: String = "",
    date: List[String] = List.empty[String]
)
