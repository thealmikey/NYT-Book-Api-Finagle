package apichallenge.client.routes.requests

import org.joda.time.DateTime

case class BooksRequest(authorName: String, years: Option[List[DateTime]])
