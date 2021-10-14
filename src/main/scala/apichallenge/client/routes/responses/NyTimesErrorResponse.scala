package apichallenge.client.routes.responses

case class Detail(
    errorcode: String
)

case class Fault(
    faultstring: String,
    detail: Detail
)

case class RateLimitResponse(
    fault: Fault
)
