package apichallenge.server.services

import apichallenge.server.models.{AuthorizationRequest, User}
import cats.effect.IO
import com.twitter.finagle.oauth2.{
  AccessToken,
  AuthInfo,
  ClientCredential,
  DataHandler,
  GrantResult
}
import com.twitter.util.Future
import dev.profunktor.redis4cats.RedisCommands
import tsec.passwordhashers.jca.BCrypt
import apichallenge.server.utils.util._

import scala.concurrent.duration._
import java.util.{Date, UUID}
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global

abstract class RedisUserDataHandler(
    var redis: RedisCommands[
      IO,
      User,
      GrantResult
    ]
) extends DataHandler[User] {
  implicit val ec: ExecutionContext = global
  def validateClient(
      maybeClientCredential: Option[ClientCredential],
      request: AuthorizationRequest
  ): Future[Boolean] = ???

  def findUser(
      maybeClientCredential: Option[ClientCredential],
      request: AuthorizationRequest
  ): Future[Option[User]] = {
    ???
  }

  def createAccessToken(authInfo: AuthInfo[User]): Future[AccessToken] =
    Future {
      AccessToken(
        token = s"ING-${UUID.randomUUID()}",
        refreshToken = Some(s"ING-${UUID.randomUUID()}"),
        scope = None,
        expiresIn = Some(10.minute.toSeconds),
        createdAt = new Date()
      )
    }

  def getStoredAccessToken(
      authInfo: AuthInfo[User]
  ): Future[Option[AccessToken]] = {
    redis.get(authInfo.user).unsafeToFuture().asTwitter(ec)
    ???
  }

  def findAuthInfoByCode(code: String): Future[Option[AuthInfo[User]]] = ???

  def findAuthInfoByRefreshToken(
      refreshToken: String
  ): Future[Option[AuthInfo[User]]] = ???

  def deleteAuthCode(code: String): Future[Unit] = ???

  def findAccessToken(token: String): Future[Option[AccessToken]] = ???

  def findAuthInfoByAccessToken(
      accessToken: AccessToken
  ): Future[Option[AuthInfo[User]]] = ???

}
