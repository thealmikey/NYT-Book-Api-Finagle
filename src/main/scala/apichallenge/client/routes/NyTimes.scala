package apichallenge.client.routes

import com.twitter.finagle.{Http, Service}
import com.twitter.finagle.http.{Method, Request, Response}
import io.circe.Decoder, io.circe.Encoder, io.circe.generic.semiauto._

object NyTimes {

  val apiHost = "http://api.nytimes.com"
  val apiSlug = "/svc/books/v3/lists/"
  val bookHistoryUrl = s"best-sellers/history.json?api_key=${apiKey}"
  val allBooksInHistory =
    Request(Method.Get, s"best-sellers/history.json?api_key=${apiKey}")
//  val apiKey = "zNdQtABZ19IRAsvf89dLq6ZQTf5mLO2P"
//  val apiKey = "NwV0o2zA1vDYGpzbtRz7jOaayEAGCZBK"
  val apiKey = "4VESd0yA8FCQs7nFGGgzSSDhzK2MEzZT"
}
