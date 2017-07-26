package helm

import argonaut._, Argonaut._

/** Case class representing a service as returned from an API call to Consul */
final case class ServiceResponse(
  service:           String,
  id:                String,
  tags:              List[String],
  address:           String,
  port:              Int,
  enableTagOverride: Boolean,
  createIndex:       Long,
  modifyIndex:       Long
)

object ServiceResponse {
  implicit def ServiceResponseDecoder: DecodeJson[ServiceResponse] =
    DecodeJson(j =>
      for {
        id                <- (j --\ "ID").as[String]
        address           <- (j --\ "Address").as[String]
        enableTagOverride <- (j --\ "EnableTagOverride").as[Boolean]
        createIndex       <- (j --\ "CreateIndex").as[Long]
        modifyIndex       <- (j --\ "ModifyIndex").as[Long]
        port              <- (j --\ "Port").as[Int]
        service           <- (j --\ "Service").as[String]
        tags              <- (j --\ "Tags").as[List[String]]
      } yield ServiceResponse(service, id, tags, address, port, enableTagOverride, createIndex, modifyIndex)
    )
}
