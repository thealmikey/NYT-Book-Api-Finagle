package apichallenge.server.models

import apichallenge.server.utils.DateUtil.Date
import eu.timepit.refined.api.Refined
import io.finch.circe._
import io.circe.generic.auto._

case class AuthorDateSearchParam(
    author: String = "",
    date: List[String] = List.empty[String]
)
