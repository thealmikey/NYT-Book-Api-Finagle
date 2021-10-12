package apichallenge.server.redis

import apichallenge.AppServer.contextShiftIO
import cats.effect.{Concurrent, ContextShift, IO, Resource}
import com.twitter.logging.Logger
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import dev.profunktor.redis4cats.codecs.Codecs
import dev.profunktor.redis4cats.codecs.splits.SplitEpi
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log.NoOp.instance
import io.circe.{Decoder, Encoder, parser}
import io.circe.syntax._
import io.circe.{Decoder, Encoder, parser}

object RedisJsonCache {

  def createServer[K: Decoder: Encoder, V: Decoder: Encoder](
      redis: RedisClient
  ): Resource[IO, RedisCommands[IO, K, V]] = {
    val codec: RedisCodec[K, V] =
      Codecs.derive(RedisCodec.Utf8, jsonConv[K], jsonConv[V])

    Redis[IO].fromClient(redis, codec)
  }

  private def jsonConv[A: Encoder: Decoder]: SplitEpi[String, A] =
    SplitEpi[String, A](
      str => parser.decode[A](str).fold(throw _, v => v),
      _.asJson.noSpaces
    )

}
