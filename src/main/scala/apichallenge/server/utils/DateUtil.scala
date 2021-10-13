package apichallenge.server.utils

import fastparse._
import NoWhitespace._
import eu.timepit.refined.api.Validate

object DateUtil {

  //inspired from answer from Refined Gitter channel
  //https://gitter.im/fthomas/refined?at=5b9d889654587954f9aab221
  final case class Date()

  def year[_: P] = P(CharIn("0-9").rep(exactly = 4))

  def month[_: P] = P(CharIn("0-1") ~ CharIn("0-9"))

  def day[_: P] = P(CharIn("0-3") ~ CharIn("0-9"))

  def date[_: P] = P(year ~ "-" ~ month ~ "-" ~ day)

  implicit class ParsedOps[A](parsed: Parsed[A]) {
    def isSuccess: Boolean =
      parsed match {
        case Parsed.Success(_, _)    => true
        case Parsed.Failure(_, _, _) => false
      }

    def errorMessage: String =
      parsed match {
        case Parsed.Success(_, _)          => "Parse success"
        case err @ Parsed.Failure(_, _, _) => err.msg
      }
  }

  def fromParser[A, B, _: P](parser: P[A], b: B): Validate.Plain[String, B] =
    Validate.fromPredicate(
      s => date(s).isSuccess,
      s => s"""${date(s).asInstanceOf[Parsed.Failure].msg} for input "$s"""",
      b
    )

  object Date {
    implicit def dateValidate[_: P] =
      fromParser(date, Date())
  }
}
