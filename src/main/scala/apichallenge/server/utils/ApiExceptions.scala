package apichallenge.server.utils

object ApiExceptions {
  sealed abstract class ApiException(
      message: String,
      cause: Option[Throwable] = None
  ) extends Exception(message, cause.orNull)
  case class RateLimitException(message: String) extends ApiException(message)
  case class MissingQueryException(message: String)
      extends ApiException(message)
  case class DisconnectionException(message: String)
      extends ApiException(message)
  case class IllegalParamsException(message: String)
      extends ApiException(message)
  case class ApiAuthorizationException(message: String)
      extends ApiException(message)
  case class GenericApiException(message: String) extends ApiException(message)
  case class JsonConvException(message: String) extends ApiException(message)

}
