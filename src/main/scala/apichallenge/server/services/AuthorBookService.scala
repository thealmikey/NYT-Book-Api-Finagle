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
  //searchApiBooksByAuthorAndDate
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
//              searchAuthorAndDateRedis(authorName, date).map(
//                _.get.asRight[ApiException]
//              )
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
