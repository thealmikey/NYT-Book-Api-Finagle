package apichallenge.client.routes.responses

import org.joda.time.DateTime
import apichallenge.client.utils

case class Isbns(
    isbn10: Option[String],
    isbn13: Option[String]
)

case class RawBook(
    title: Option[String],
    description: Option[String],
    contributor: Option[String],
    author: Option[String],
    contributor_note: Option[String],
    price: Option[String],
    age_group: Option[String],
    publisher: Option[String],
    isbns: Option[List[Isbns]],
    ranks_history: Option[List[RankHistory]],
    reviews: Option[List[Reviews]]
)

case class Reviews(
    book_review_link: Option[String],
    first_chapter_link: Option[String],
    sunday_review_link: Option[String],
    article_chapter_link: Option[String]
)

case class RawBookResponse(
    status: Option[String],
    copyright: Option[String],
    num_results: Option[Int],
    results: Option[List[RawBook]]
)

case class RankHistory(
    primary_isbn10: Option[String],
    primary_isbn13: Option[String],
    rank: Option[Int],
    list_name: Option[String],
    display_name: Option[String],
    published_date: Option[String],
    bestsellers_date: Option[String],
    weeks_on_list: Option[Int],
    rank_last_week: Option[Int],
    asterisk: Option[Int],
    dagger: Option[Int]
)
