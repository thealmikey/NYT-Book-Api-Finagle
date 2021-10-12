package apichallenge.client.routes.responses

case class BookCategory(
    list_name: String,
    display_name: String,
    list_name_encoded: String,
    oldest_published_date: String,
    newest_published_date: String,
    updated: String
)

case class RawBookCategoriesResponse(
    status: String,
    copyright: String,
    num_results: Int,
    results: List[BookCategory]
)
