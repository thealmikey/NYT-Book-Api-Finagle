package apichallenge.server.services

import apichallenge.server.models.{
  ApiClient,
  AuthorizationRequest,
  User,
  UserHashed
}
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
import tsec.passwordhashers.PasswordHash
import tsec.passwordhashers.jca.BCrypt
import apichallenge.server.utils.util._

import scala.concurrent.duration._
import java.util.{Date, UUID}
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global

case class RedisUserDataHandler(
    redis: RedisCommands[IO, UserHashed, AccessToken],
    clientToUser: RedisCommands[IO, ApiClient, UserHashed],
    apiStringStore: RedisCommands[IO, String, AccessToken],
    tokenUserRedis: RedisCommands[IO, String, UserHashed],
    apiUserStore: RedisCommands[IO, User, UserHashed]
) extends DataHandler[UserHashed] {
  implicit val ec: ExecutionContext = global

  def storeHashedUser(
      user: User
  ): Future[Option[UserHashed]] = {
    BCrypt
      .hashpw[IO](user.password)
      .flatMap { hash =>
        var hashedUser = UserHashed(user.username, hash)
        apiUserStore
          .set(user, UserHashed(user.username, hash))
          .as(Option(hashedUser))
      }
      .unsafeToFuture()
      .asTwitter
  }

  def createHashedUser(user: User) = {
    BCrypt
      .hashpw[IO](user.password)
      .map { hash =>
        var hashedUser = UserHashed(user.username, hash)
        hashedUser
      }
  }

  def createAuthInfo(userHashed: UserHashed): AuthInfo[UserHashed] = {
    AuthInfo[UserHashed](
      userHashed,
      "",
      None,
      None
    )
  }

  def findTheAccessToken(token: String): Option[AccessToken] = {
    apiStringStore.get(token).unsafeRunSync()
  }

  def createAccessToken(authInfo: AuthInfo[UserHashed]): Future[AccessToken] =
    Future {
      AccessToken(
        token = s"ING-${UUID.randomUUID()}",
        refreshToken = Some(s"ING-${UUID.randomUUID()}"),
        scope = None,
        expiresIn = Some(10.minute.toSeconds),
        createdAt = new Date()
      )
    }.flatMap { token =>
      redis
        .set(authInfo.user, token)
        .as(token)
        .flatMap { token =>
          apiStringStore.set(token.token, token)
        }
        .as(token)
        .unsafeToFuture()
        .asTwitter
    }

  def getStoredAccessToken(
      authInfo: AuthInfo[UserHashed]
  ): Future[Option[AccessToken]] = {
    redis.get(authInfo.user).unsafeToFuture().asTwitter(ec)
  }

  override def validateClient(
      clientId: String,
      clientSecret: String,
      grantType: String
  ): Future[Boolean] = {
    apiStringStore
      .get(clientId)
      .unsafeToFuture()
      .map { res =>
        res match {
          case Some(value) => true
          case None        => true
        }
      }
      .asTwitter
  }

  override def findUser(
      username: String,
      password: String
  ): Future[Option[UserHashed]] = {
    apiUserStore.get(User(username, password)).unsafeToFuture().asTwitter
  }

  private[this] def makeToken: AccessToken = {
    AccessToken(
      token = s"AT-${UUID.randomUUID()}",
      refreshToken = Some(s"RT-${UUID.randomUUID()}"),
      scope = None,
      expiresIn = Some(1.minute.toSeconds),
      createdAt = new Date()
    )
  }

  override def refreshAccessToken(
      authInfo: AuthInfo[UserHashed],
      refreshToken: String
  ): Future[AccessToken] = {
    createAccessToken(authInfo).flatMap { info =>
      redis
        .get(authInfo.user)
        .flatMap { token =>
          token match {
            case Some(value) => {
              redis.set(authInfo.user, info).as(info)
            }
          }
        }
        .unsafeToFuture()
        .asTwitter
    }
  }

  override def findAuthInfoByCode(
      code: String
  ): Future[Option[AuthInfo[UserHashed]]] = {
    tokenUserRedis
      .get(code)
      .map { client =>
        client match {
          case Some(ad) =>
            Some(
              AuthInfo[UserHashed](
                ad,
                "",
                None,
                None
              )
            )

        }
      }
      .unsafeToFuture()
      .asTwitter
  }

//  override def findAuthInfoByRefreshToken(
//      refreshToken: String
//  ): Future[Option[AuthInfo[OAuthUser]]] = {
//    accessTokens.values.find { at: AccessToken =>
//      at.refreshToken.exists(_.equals(refreshToken))
//    } match {
//      case Some(at) => Future.value(authInfosByAccessToken.get(at.token))
//      case None     => Future.value(None)
//    }
//  }

//  override def findClientUser(
//      clientId: String,
//      clientSecret: String,
//      scope: Option[String]
//  ): Future[Option[OAuthUser]] = {
//    clients.find {
//      case ad =>
//        clientId.equals(ad.clientId) && clientSecret.equals(ad.clientSecret)
//    } match {
//      case Some(ad) => Future.value(Some(ad.user))
//      case None     => Future.value(None)
//    }
//  }

  override def findAccessToken(token: String): Future[Option[AccessToken]] = {
    apiStringStore.get(token).unsafeToFuture().asTwitter
  }

  override def findAuthInfoByAccessToken(
      accessToken: AccessToken
  ): Future[Option[AuthInfo[UserHashed]]] = {
    tokenUserRedis
      .get(accessToken.token)
      .map { res =>
        res match {
          case Some(value) => {
            Some(
              AuthInfo[UserHashed](
                value,
                "",
                None,
                None
              )
            )
          }
        }
      }
      .unsafeToFuture()
      .asTwitter
  }

  override def findAuthInfoByRefreshToken(
      refreshToken: String
  ): Future[Option[AuthInfo[UserHashed]]] = {
    Future {
      None
    }
  }

  def registerUser(name: String, password: String): Future[Option[String]] = {
    var user = User(name, password)
    var token = makeToken
    storeHashedUser(user).flatMap { hashedUser =>
      hashedUser match {
        case Some(value) =>
          tokenUserRedis
            .set(token.token, value)
            .flatMap { user =>
              clientToUser.set(
                ApiClient(name, password),
                hashedUser.get
              )
            }
            .as(Some(token.token))
            .unsafeToFuture()
            .asTwitter
      }
    }

  }

  override def findClientUser(
      clientId: String,
      clientSecret: String,
      scope: Option[String]
  ): Future[Option[UserHashed]] = {
    clientToUser
      .get(ApiClient(clientId, clientSecret))
      .unsafeToFuture()
      .asTwitter
  }
}
