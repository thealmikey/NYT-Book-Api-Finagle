package apichallenge.server.services

import apichallenge.client.routes.responses.{RankHistory, RawBook}
import apichallenge.client.services.NyTimesService
import apichallenge.server.models.{
  AuthorBookSearchResult,
  RawAuthorDateSearchResults,
  AuthorNameWithBooks,
  Book
}
import apichallenge.server.redis.AuthorBookRedisStore
import cats.effect.IO
import org.joda.time.format.DateTimeFormat

class AuthorBookService(
    val authorBookRedisStore: AuthorBookRedisStore,
    var nyTimesClientService: NyTimesService
) {
  def mapRawToApiResult(
      authorName: String,
      date: List[String] = List.empty[String]
  ): IO[AuthorBookSearchResult] = {
    searchApiBooksByAuthorAndDate(authorName, date)
      .map {
        combineSearchTermWithApiResults(authorName, date, _)
      }
  }
  //searchApiBooksByAuthorAndDate
  def searchApiBooksByAuthorAndDate(
      authorName: String,
      date: List[String] = List.empty[String],
      offset: Int = 20
  ): IO[RawAuthorDateSearchResults] = {
    nyTimesClientService
      .searchBooksByAuthorName(authorName, offset)
      .flatMap(bookFetch =>
        bookFetch match {
          case (0, None) => IO(RawAuthorDateSearchResults())
          case (n, Some(books)) => {
            if (n < 20) {
              IO { RawAuthorDateSearchResults(books) }
            } else {
              //because we already traversed the first page we
              //don't need the 'offsset=20'
              val pageList = List.range(20, n, 20)
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
            }.map(value => (RawAuthorDateSearchResults(value.get)))
          }
        }
      )
  }

  def combineSearchTermWithApiResults(
      authorSearchTerm: String,
      dates: List[String],
      res: RawAuthorDateSearchResults
  ): AuthorBookSearchResult = {
    val rawBooks = if (!dates.isEmpty) {
      dates
        .map(searchDate => filterBooksBySearchDate(searchDate, res.books))
        .flatten
    } else {
      res.books
    }
    var booksWithAuthor = pairAuthorWithTheirBooks(rawBooks)
    AuthorBookSearchResult(
      authorSearchTerm.replaceAll("_", " "),
      Some(booksWithAuthor.toList)
    )
  }

  def searchAuthorAndDateRedis(
      authorName: String,
      date: List[String] = List.empty[String]
  ): IO[Option[AuthorBookSearchResult]] = {
    authorBookRedisStore
      .fetchSearchResults(authorName, date)
      .map { res =>
        res.map(combineSearchTermWithApiResults(authorName, date, _))
      }
  }

  def searchBooksByAuthorAndDate(
      authorName: String,
      date: List[String] = List.empty[String]
  ): IO[AuthorBookSearchResult] = {
    searchAuthorAndDateRedis(authorName, date).flatMap { searchRes =>
      searchRes match {
        case Some(value) => IO(value)
        case None => {
          searchApiBooksByAuthorAndDate(authorName, date)
            .flatMap { searchRes =>
              authorBookRedisStore.addSearchResults(
                authorName,
                date,
                searchRes.books
              )
            }
            .flatMap { res =>
              searchAuthorAndDateRedis(authorName, date).map(_.get)
            }
        }
      }
    }
  }

  def pairAuthorWithTheirBooks(
      rawbooks: List[RawBook]
  ): List[AuthorNameWithBooks] = {
    rawbooks
      .groupBy(_.author)
      .map(authorBookTuple => {
        var rawAuthorBooks = authorBookTuple._2
        var authorBooks = rawAuthorBooks
          .map(mapRawBookToApiBook(_))
        AuthorNameWithBooks(
          authorBookTuple._1.get,
          Some(
            authorBooks
          )
        )
      })
      .toList
  }

  def mapRawBookToApiBook(rawBook: RawBook): Book = {
    Book(
      rawBook.title.get,
      rawBook.publisher,
      getEarliestPublishDate(rawBook.ranks_history)
    )
  }

  def filterBooksBySearchDate(
      date: String,
      books: List[RawBook]
  ): List[RawBook] = {
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
