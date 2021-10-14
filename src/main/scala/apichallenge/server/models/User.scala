package apichallenge.server.models

case class User(
    email: String,
    name: String,
    password: String,
    hashedPassword: String,
    age: Int = 0,
    height: Int = 0
)
case class AuthorizationRequest(name: String, plainPassword: String)
