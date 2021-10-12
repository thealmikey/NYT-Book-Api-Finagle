package apichallenge.client.utils

import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

object Api {
  sealed abstract class ApiError
  case class ServerError(statusCode: Int) extends ApiError
  case class DecodeError(message: String) extends ApiError

  val formatter = DateTimeFormat.forPattern("yyyy-MM-dd")

}
