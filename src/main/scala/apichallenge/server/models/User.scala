package apichallenge.server.models

case class User(
    username: String,
    password: String
)
case class AuthorizationRequest(name: String, plainPassword: String)
