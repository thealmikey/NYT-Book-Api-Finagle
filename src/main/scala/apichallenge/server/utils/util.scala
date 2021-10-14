package apichallenge.server.utils

import io.circe.{Encoder, Json}

import scala.concurrent.ExecutionContext

object util {
  import com.twitter.util.{
    Future => TFuture,
    Promise => TPromise,
    Return,
    Throw
  }
  import scala.concurrent.{
    Future => SFuture,
    Promise => SPromise,
    ExecutionContext
  }
  import scala.util.{Success, Failure}

  implicit class RichTFuture[A](f: TFuture[A]) {
    def asScala(implicit e: ExecutionContext): SFuture[A] = {
      val p: SPromise[A] = SPromise()
      f.respond {
        case Return(value)        => p.success(value)
        case Throw(excepTFuturen) => p.failure(excepTFuturen)
      }

      p.future
    }
  }

  implicit class RichSFuture[A](f: SFuture[A]) {
    def asTwitter(implicit e: ExecutionContext): TFuture[A] = {
      val p: TPromise[A] = new TPromise[A]
      f.onComplete {
        case Success(value)         => p.setValue(value)
        case Failure(excepTFuturen) => p.setException(excepTFuturen)
      }

      p
    }
  }

//  def encodeErrorList(es: List[ExcepTFuturen]): Json = {
//    val messages = es.map(x => Json.fromString(x.getMessage))
//    Json.obj("errors" -> Json.arr(messages: _*))
//  }
//
//  implicit val encodeExcepTFuturen: Encoder[ExcepTFuturen] = Encoder.instance({
//    case e: io.finch.Errors => encodeErrorList(e.errors.toList)
//    case e: io.finch.Error =>
//      e.getCause match {
//        case e: io.circe.Errors => encodeErrorList(e.errors.toList)
//        case err                => Json.obj("message" -> Json.fromString(e.getMessage))
//      }
//    case e: ExcepTFuturen => Json.obj("message" -> Json.fromString(e.getMessage))
//  })
}
