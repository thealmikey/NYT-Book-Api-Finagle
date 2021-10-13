package apichallenge.server.models

sealed trait SearchResults
case class AuthorBookSearchResult(
    authorSearchTerm: String,
    authorWithBooks: Option[List[AuthorNameWithBooks]] = None
) extends SearchResults
case class ErrorResult(
    message: String
) extends SearchResults
