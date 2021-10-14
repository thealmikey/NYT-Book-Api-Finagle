//package apichallenge.client.utils
//
//import com.twitter.finagle.http.Response
//import com.twitter.finagle.service.{ReqRep, ResponseClassifier}
//import io.circe.Decoder.Result
//import io.circe.{Decoder, Encoder, HCursor, Json}
//import org.joda.time.DateTime
//import org.joda.time.format.DateTimeFormat
//
//import scala.util.Success
//
//object Api {
//  sealed abstract class ApiError
//  case class ServerError(statusCode: Int) extends ApiError
//  case class DecodeError(message: String) extends ApiError
//
//  val formatter = DateTimeFormat.forPattern("yyyy-MM-dd")
//  val responseClassifier =
//    ResponseClassifier.named("NytimesResponseClasssifier") {
//      case ReqRep(_, res: Response) => ResponseClass.RetryableFailure
//      case ReqRep(_, Throw(Failure(Some(_: UtilTimeoutException)))) =>
//        ResponseClass.RetryableFailure
//      case ReqRep(_, Throw(_: TimeoutException)) =>
//        ResponseClass.RetryableFailure
//      case ReqRep(_, Throw(_: UtilTimeoutException)) =>
//        ResponseClass.RetryableFailure
//      case ReqRep(_, Throw(_: ChannelClosedException)) =>
//        ResponseClass.RetryableFailure
//      case ReqRep(_, Throw(ChannelWriteException(Some(_: TimeoutException)))) =>
//        ResponseClass.RetryableFailure
//      case ReqRep(
//            _,
//            Throw(ChannelWriteException(Some(_: UtilTimeoutException)))
//          ) =>
//        ResponseClass.RetryableFailure
//      case ReqRep(
//            _,
//            Throw(ChannelWriteException(Some(_: ChannelClosedException)))
//          ) =>
//        ResponseClass.RetryableFailure
//    }
//
//}
