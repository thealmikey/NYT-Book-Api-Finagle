package apichallenge.server.models

case class User(
    name: String,
    password: String
)
case class AuthorizationRequest(name: String, plainPassword: String)
