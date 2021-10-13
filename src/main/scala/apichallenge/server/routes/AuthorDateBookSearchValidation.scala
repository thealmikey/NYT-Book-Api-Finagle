package apichallenge.server.routes
import io.finch._
import org.joda.time.format.DateTimeFormat

object AuthorDateBookSearchValidation {
  import scala.util.Try
  val fmt = DateTimeFormat forPattern "yyyy-MM-dd"
  def validate(date: String) = Try(fmt.parseDateTime(date))
}
