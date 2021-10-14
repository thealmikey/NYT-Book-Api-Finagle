package apichallenge.server.utils

import io.circe.{Encoder, Json}

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
        case Return(value)    => p.success(value)
        case Throw(exception) => p.failure(exception)
      }

      p.future
    }
  }

  implicit class RichSFuture[A](f: SFuture[A]) {
    def asTwitter(implicit e: ExecutionContext): TFuture[A] = {
      val p: TPromise[A] = new TPromise[A]
      f.onComplete {
        case Success(value)     => p.setValue(value)
        case Failure(exception) => p.setException(exception)
      }

      p
    }
  }

  def encodeErrorList(es: List[Exception]): Json = {
    val messages = es.map(x => Json.fromString(x.getMessage))
    Json.obj("errors" -> Json.arr(messages: _*))
  }

  implicit val encodeException: Encoder[Exception] = Encoder.instance({
    case e: io.finch.Errors => encodeErrorList(e.errors.toList)
    case e: io.finch.Error =>
      e.getCause match {
        case e: io.circe.Errors => encodeErrorList(e.errors.toList)
        case err                => Json.obj("message" -> Json.fromString(e.getMessage))
      }
    case e: Exception => Json.obj("message" -> Json.fromString(e.getMessage))
  })
}
