package apichallenge.server.services

import apichallenge.client.routes.responses.{RankHistory, RawBook}
import apichallenge.client.services.NyTimesService
import apichallenge.server.models.{
  AuthorBookSearchResult,
  AuthorNameWithBooks,
  Book,
  RawAuthorDateSearchResults
}
import apichallenge.server.redis.AuthorBookRedisStore
import apichallenge.server.utils.ApiExceptions.ApiException
import cats.effect.IO
import org.joda.time.format.DateTimeFormat
import cats.syntax.either._ // for asRight

class AuthorBookService(
    val authorBookRedisStore: AuthorBookRedisStore,
    var nyTimesClientService: NyTimesService
) {
  /*
  This function fetch raw data from the API and maps it into a `case class AuthorBookSearchResult`
  This case class can then be presented as JSON
   */
  def mapRawToApiResult(
      authorName: String,
      date: List[String] = List.empty[String]
  ): IO[Either[ApiException, AuthorBookSearchResult]] = {
    searchApiBooksByAuthorAndDate(authorName, date)
      .map { result =>
        result match {
          case Left(value) => value.asLeft[AuthorBookSearchResult]
          case Right(value) =>
            Right(combineSearchTermWithApiResults(authorName, date, value))
        }
      }
  }
  /*
  This function gets back raw data from the API, as the data has more fields we still
  need to map it into a presentable manner with the function above `def mapRawToApiResult`
  than needed for the API, e.g. Book data has fields like age-group and isbns which
  are not needed for our particular use case.

  The data returned from the Books API is paged and the function fetches by making
  multiple calls to the endpoint.
  This function can be improved to support concurrency as each page after the first
  (which contains the seed data to tell us the total books) can be fetched individually
  and combined
   */

  def searchApiBooksByAuthorAndDate(
      authorName: String,
      date: List[String] = List.empty[String],
      offset: Int = 20
  ): IO[Either[ApiException, RawAuthorDateSearchResults]] = {
    nyTimesClientService
      .searchBooksByAuthorName(authorName, offset)
      .flatMap(bookFetchEither =>
        bookFetchEither match {
          case Left(exception) =>
            IO(exception.asLeft[RawAuthorDateSearchResults])
          case Right(bookFetch) => {
            bookFetch match {
              case (0, None) => IO(Right(RawAuthorDateSearchResults()))
              case (n, Some(books)) => {
                if (n < 20) {
                  IO { Right(RawAuthorDateSearchResults(books)) }
                } else {
                  //because we already traversed the first page we
                  //don't need the 'offsset=20'
                  val pageList = List.range(20, n, 20)
                  pageList
                    .map(page =>
                      nyTimesClientService
                        .searchBooksByAuthorName(authorName, page)
                    )
                    .foldLeft(IO(Option(books)))(
                      (firstReqData, followReqData) => {
                        firstReqData.flatMap { firstReqBooks =>
                          followReqData.map(followReqBooks =>
//                            followReqBooks._2
                            followReqBooks match {
                              case Right(value) =>
                                value._2
                                  .map(books => books ++ firstReqBooks.get)
                              case Left(exception) => None
                            }
                          )
                        }
                      }
                    )
                }.map(value => (Right(RawAuthorDateSearchResults(value.get))))
              }
            }
          }
        }
      )
  }

  /*
  This function creates the final Data structure to present to the End-User of the API.
  It returns a JSON with the search term fed to the API followed by a set of Authors paired up
  with the books they've written
   */

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

  /*
  This function first checks the search data in Redis before defaulting
  to the API. If data is absent in Redis, it's fetched, then persisted, then
  presented to the API end user.  The data has a ttl of `3 minutes`

  The ttl is set in `AuthorBookRedisStore` using Redis Command `redis.ttl(key:Key, t:FiniteDuration)`
   */

  def searchBooksByAuthorAndDate(
      authorName: String,
      date: List[String] = List.empty[String]
  ): IO[Either[ApiException, AuthorBookSearchResult]] = {
    searchAuthorAndDateRedis(authorName, date).flatMap { searchRes =>
      searchRes match {
        case Some(value) => IO(value.asRight)
        case None => {
          searchApiBooksByAuthorAndDate(authorName, date)
            .flatMap { searchRes =>
              searchRes match {
                case Left(value) => IO(value.asLeft[AuthorBookSearchResult])
                case Right(value) =>
                  authorBookRedisStore
                    .addSearchResults(
                      authorName,
                      date,
                      searchRes.toOption.get.books
                    )
                    .map(
                      combineSearchTermWithApiResults(authorName, date, _)
                        .asRight[ApiException]
                    )
              }
            }
            .map { res =>
              res match {
                case excep @ Left(value)       => excep
                case correctRes @ Right(value) => correctRes
              }
            }
        }
      }
    }
  }

  /*
   Authors can have multiple books in the NYT list.
   This function creates a structure that takes all the books from one author in an array.
   The author name and the book list are in one Json object
   */

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
          authorBookTuple._1,
          Some(
            authorBooks
          )
        )
      })
      .toList
  }

  /*
  This function removes the extraneous fields from the `RawBook` from the API.
  It simplifies it by leaving only the relevant fields.
  Also it seems that books seem to have multiple `publish_date` data from `Rank History`
  object. I used the oldest value from the multiple publish dates.
   */
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

  /*
  This function iterates through the `rank_history` List that is present for every
  book. It picks the oldest publish date and returns is an Option[String].
  The reason we use Option is that sometimes in the API there's missing fields that
  come back as null. Circe enables us to encode them into None using derivation powered
  by Shapeless
   */
  def getEarliestPublishDate(
      rankHistoryOption: Option[List[RankHistory]]
  ): Option[String] = {
    rankHistoryOption match {
      case Some(rankHistory) => {
        val formatter = DateTimeFormat.forPattern("yyyy-MM-dd")
        val oldestPublishDate = rankHistory
          .map(rank => formatter.parseDateTime(rank.published_date.get))
          .map(_.getMillis)
          .sortWith(_ < _)
          .map(formatter.print(_))
          .headOption
        oldestPublishDate
      }
      case None => None
    }
  }
}
