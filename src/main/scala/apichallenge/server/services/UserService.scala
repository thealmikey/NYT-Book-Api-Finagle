package apichallenge.server.services

import apichallenge.server.models.User
import cats.effect.IO
import com.twitter.finagle.client.Transporter.Credentials
import com.twitter.finagle.oauth2.AccessToken
import com.twitter.util.Future
import tsec.passwordhashers.jca.BCrypt

class UserService(userDataHandler: RedisUserDataHandler) {
  def signup(
      name: String,
      email: String,
      password: String
  ): IO[AccessToken] = {
//    val hashedPwd = BCrypt.hashpw(password)
//    var user = User(name, email, password, hashedPwd)
    ???
  }

  def login(email: String, password: String): Future[Credentials] = {
    ???
  }

}
