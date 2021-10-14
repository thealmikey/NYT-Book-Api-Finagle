package apichallenge.shared.config
import pureconfig._
import pureconfig.generic.auto._

case class HostPort(
    host: String,
    port: Int
)
case class NytApiKey(key: String)

case class AppServerConf(
    redis: HostPort,
    appServer: HostPort,
    nytApiKey: NytApiKey
)
