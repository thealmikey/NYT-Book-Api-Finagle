package apichallenge.shared.config
import pureconfig._
import pureconfig.generic.auto._

case class HostPort(
    host: Option[String],
    port: Option[Int]
)
case class NytApiKey(key: String) extends AnyVal

case class AppServerConf(
    redis: HostPort,
    appServer: HostPort,
    nyTimesKey: NytApiKey
)
