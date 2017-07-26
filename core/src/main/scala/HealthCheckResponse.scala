package helm

import argonaut._, Argonaut._

/** Case class representing a health check as returned from an API call to Consul */
final case class HealthCheckResponse(
  node:        String,
  checkId:     String,
  name:        String,
  status:      HealthStatus,
  notes:       String,
  output:      String,
  serviceId:   String,
  serviceName: String,
  serviceTags: List[String],
  createIndex: Long,
  modifyIndex: Long
)

object HealthCheckResponse {
  implicit def HealthCheckResponseDecoder: DecodeJson[HealthCheckResponse] =
    DecodeJson(j =>
      for {
        node        <- (j --\ "Node").as[String]
        checkId     <- (j --\ "CheckID").as[String]
        name        <- (j --\ "Name").as[String]
        status      <- (j --\ "Status").as[HealthStatus]
        notes       <- (j --\ "Notes").as[String]
        output      <- (j --\ "Output").as[String]
        serviceId   <- (j --\ "ServiceID").as[String]
        serviceName <- (j --\ "ServiceName").as[String]
        serviceTags <- (j --\ "ServiceTags").as[List[String]]
        createIndex <- (j --\ "CreateIndex").as[Long]
        modifyIndex <- (j --\ "ModifyIndex").as[Long]
      } yield HealthCheckResponse(
        node,
        checkId,
        name,
        status,
        notes,
        output,
        serviceId,
        serviceName,
        serviceTags,
        createIndex,
        modifyIndex)
    )
}
