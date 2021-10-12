package apichallenge.server.services

import apichallenge.client.routes.responses.{RankHistory, RawBook}
import apichallenge.client.services.NyTimesService
import apichallenge.server.models.{
  AuthorBookSearchResult,
  AuthorDateSearchResults,
  AuthorNameWithBooks,
  Book
}
import apichallenge.server.redis.AuthorBookRedisStore
import cats.effect.IO
import org.joda.time.format.DateTimeFormat

case class BooksPaged(
    totalPages: Int,
    books: List[RawBook],
    inProgress: Set[Int],
    unvisited: Set[Int]
)
//object BooksPaged {
//  def setUnvisitedFromTotalPages(totalPages: Int): Set[Int] = {}
//}

class AuthorBookService(
    val authorBookRedisStore: AuthorBookRedisStore,
    var nyTimesClientService: NyTimesService
) {
  def searchApiBooksByAuthorAndDate(
      authorName: String,
      date: List[String] = List.empty[String]
  ): IO[AuthorBookSearchResult] = {
    nyTimesClientService
      .searchBooksByAuthorName(
        authorName
      )
      .flatMap(bookFetch =>
        bookFetch match {
          case (0, None) => IO(AuthorDateSearchResults())
          case (n, Some(books)) => {
            if (n < 20) {
              IO { AuthorDateSearchResults(books) }
            } else {
              //because we already traversed the first page we
              //don't need the 'offsset=20'
              var pageList =
                if (n % 20 != 0) List.range(0, n + 20, 20).tail
                else List.range(0, n, 20)
              pageList
                .map(page =>
                  nyTimesClientService
                    .searchBooksByAuthorName(authorName, page)
                )
                .foldLeft(IO(Option(books)))((firstReqData, followReqData) => {
                  firstReqData.flatMap { firstReqBooks =>
                    followReqData.map(followReqBooks =>
                      followReqBooks._2.map(books => books ++ firstReqBooks.get)
                    )
                  }
                })
            }.map(value => (AuthorDateSearchResults(value.get)))
          }
        }
      )
      .map { authorDateSearchRes =>
        if (!date.isEmpty) {
          val booksWithAuthor = date
            .map(searchDate =>
              filterBooksByDate(searchDate, authorDateSearchRes.books)
            )
            .flatten
            .groupBy(_.author)
            .map(authorBookTuple => {
              var rawAuthorBooks = authorBookTuple._2
              var authorBooks = rawAuthorBooks
                .map(rawBook => {
                  var earliestPublishDate =
                    getEarliestPublishDate(rawBook.ranks_history)
                  Book(
                    rawBook.title.get,
                    rawBook.publisher,
                    earliestPublishDate
                  )
                })
              AuthorNameWithBooks(
                authorBookTuple._1.get,
                Some(
                  authorBooks
                )
              )
            })
          AuthorBookSearchResult(
            authorName.replaceAll("_", " "),
            Some(booksWithAuthor.toList)
          )
        } else {
          var rawBooks = authorDateSearchRes.books
          var booksWithAuthor = rawBooks
            .groupBy(_.author)
            .map(authorBookTuple => {
              var rawAuthorBooks = authorBookTuple._2
              var authorBooks = rawAuthorBooks
                .map(rawBook => {
                  var earliestPublishDate =
                    getEarliestPublishDate(rawBook.ranks_history)
                  Book(
                    rawBook.title.get,
                    rawBook.publisher,
                    earliestPublishDate
                  )
                })
              AuthorNameWithBooks(
                authorBookTuple._1.get,
                Some(
                  authorBooks
                )
              )
            })
          AuthorBookSearchResult(
            authorName.replaceAll("_", " "),
            Some(booksWithAuthor.toList)
          )
        }
      }
  }

  def searchApiAndRedis( authorName: String,
  date: List[String] = List.empty[String]
  ): IO[AuthorBookSearchResult] = {{

  }

  def filterBooksByDate(date: String, books: List[RawBook]): List[RawBook] = {
    books.filter({ book =>
      var bookDates = book.ranks_history
        .getOrElse(List.empty[RankHistory])
        .map(_.published_date.getOrElse(""))
      !bookDates.filter(bookDate => bookDate.startsWith(date)).isEmpty
    })
  }

  def getEarliestPublishDate(
      rankHistoryOption: Option[List[RankHistory]]
  ): Option[String] = {
    rankHistoryOption match {
      case Some(rankHistory) => {
        val formatter = DateTimeFormat.forPattern("yyyy-MM-dd")
        val oldestPublishDate = rankHistory
          .map(rank => formatter.parseDateTime(rank.published_date.get))
          .sortBy(_.getMillis)
          .map(formatter.print(_))
          .headOption
        oldestPublishDate
      }
      case None => None
    }
  }
}
