package apichallenge.server.utils

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
}
