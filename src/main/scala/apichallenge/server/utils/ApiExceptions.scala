package apichallenge.server.utils

import com.twitter.finagle.{Failure, FailureFlags}

object ApiExceptions {

//  val ff = Failure.ada

  sealed trait ApiException
  case class RateLimitException(
      value: String,
      cause: Throwable,
      flags: Long = FailureFlags.NonRetryable
  ) extends Exception(value, cause)
      with FailureFlags[RateLimitException]
      with ApiException {

    override protected def copyWithFlags(flags: Long): RateLimitException =
      RateLimitException(value, cause, flags)
  }

  case class ApiAuthorizationException(
      value: String,
      cause: Throwable,
      flags: Long = FailureFlags.NonRetryable
  ) extends Exception(value, cause)
      with FailureFlags[ApiAuthorizationException]
      with ApiException {

    override protected def copyWithFlags(
        flags: Long
    ): ApiAuthorizationException =
      ApiAuthorizationException(value, cause, flags)
  }

  case class AppGenericException(
      value: String,
      cause: Throwable,
      flags: Long = FailureFlags.NonRetryable
  ) extends Exception(value, cause)
      with FailureFlags[AppGenericException]
      with ApiException {

    override protected def copyWithFlags(
        flags: Long
    ): AppGenericException =
      AppGenericException(value, cause, flags)
  }
}
