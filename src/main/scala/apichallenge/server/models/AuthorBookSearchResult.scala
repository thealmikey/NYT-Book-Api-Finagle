package apichallenge.server.models

case class AuthorBookSearchResult(
    authorSearchTerm: String,
    authorWithBooks: Option[List[AuthorNameWithBooks]] = None
)
