package apichallenge.server.utils

import com.twitter.finagle.FailureFlags

object ApiExceptions {

  sealed trait ApiException
  case class RateLimitException(
      value: String,
      flags: Long = FailureFlags.NonRetryable
  ) extends FailureFlags[RateLimitException]
      with ApiException {

    override protected def copyWithFlags(flags: Long): RateLimitException =
      RateLimitException(value, flags)
  }
  case class MissingQueryException(
      value: String,
      flags: Long = FailureFlags.NonRetryable
  ) extends FailureFlags[MissingQueryException]
      with ApiException {

    override protected def copyWithFlags(flags: Long): MissingQueryException =
      MissingQueryException(value, flags)
  }
  case class DisconnectionException(
      value: String,
      flags: Long = FailureFlags.NonRetryable
  ) extends FailureFlags[DisconnectionException]
      with ApiException {

    override protected def copyWithFlags(flags: Long): DisconnectionException =
      DisconnectionException(value, flags)
  }
  case class IllegalParamsException(
      value: String,
      flags: Long = FailureFlags.NonRetryable
  ) extends FailureFlags[IllegalParamsException]
      with ApiException {

    override protected def copyWithFlags(flags: Long): IllegalParamsException =
      IllegalParamsException(value, flags)
  }
  case class ApiAuthorizationException(
      value: String,
      flags: Long = FailureFlags.NonRetryable
  ) extends FailureFlags[ApiAuthorizationException]
      with ApiException {

    override protected def copyWithFlags(
        flags: Long
    ): ApiAuthorizationException =
      ApiAuthorizationException(value, flags)
  }

  case class AppGenericException(
      value: String,
      flags: Long = FailureFlags.NonRetryable
  ) extends FailureFlags[AppGenericException]
      with ApiException {

    override protected def copyWithFlags(
        flags: Long
    ): AppGenericException =
      AppGenericException(value, flags)
  }
}
