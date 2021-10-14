package apichallenge.client.utils

import com.twitter.finagle.http.Response
import com.twitter.finagle.service.{ReqRep, ResponseClass, ResponseClassifier}
import com.twitter.util.{Throw, TimeoutException}
import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scala.util.Success

object Api {
  sealed abstract class ApiError
  case class ServerError(statusCode: Int) extends ApiError
  case class DecodeError(message: String) extends ApiError

  val nytResponseClassifier =
    ResponseClassifier.named("NytimesResponseClasssifier") {
      case ReqRep(_, res: Response) => {
        if (res.statusCode.toString.startsWith("4")) {
          ResponseClass.NonRetryableFailure
        } else if (res.statusCode.toString.startsWith("5")) {
          ResponseClass.RetryableFailure
        } else {
          ResponseClass.Success
        }
      }
    }
}
