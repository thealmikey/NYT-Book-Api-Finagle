package apichallenge.server.models

case class Book(
    name: String,
    publisher: Option[String],
    publish_date: Option[String]
)
