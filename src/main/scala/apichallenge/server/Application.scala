package apichallenge.server

import cats.effect.{Blocker, Resource}

//final case class Application[F[_]](
//    session: Session[F],
//    repos: Repositories[F],
//    services: Services[F],
//    security: Security[F],
//    resolvers: Resolvers[F],
//    api: Api[F],
//    httpApp: HttpApp[F],
//    logger: Logger[F],
//    blocker: Blocker
//)
//
//object Application {
//  def createServer[F[_]]: Resource[F, Application[F]]
//}
