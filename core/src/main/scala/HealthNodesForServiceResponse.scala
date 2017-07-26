package helm

import argonaut._, Argonaut._

/** Case class representing the response to the Health API's List Nodes For Service function */
final case class HealthNodesForServiceResponse(
  node:        NodeResponse,
  service:     ServiceResponse,
  checks:      List[HealthCheckResponse]
)

object HealthNodesForServiceResponse {
  implicit def HealthNodesForServiceResponseDecoder: DecodeJson[HealthNodesForServiceResponse] =
    DecodeJson(j =>
      for {
        node    <- (j --\ "Node").as[NodeResponse]
        service <- (j --\ "Service").as[ServiceResponse]
        checks  <- (j --\ "Checks").as[List[HealthCheckResponse]]
      } yield HealthNodesForServiceResponse(node, service, checks)
    )
}
