package helm

import scalaz._, Scalaz._
import argonaut._, Argonaut._

/** Case class representing the representation of a service as returned from an API call to Consul */
final case class ServiceResponse(
  service:           String,
  id:                String,
  tags:              List[String],
  address:           String,
  port:              Int,
  enableTagOverride: Boolean,
  modifyIndex:       Int
)

object ServiceResponse {
  implicit def ServiceResponseDecoder: DecodeJson[ServiceResponse] =
    DecodeJson(j =>
      for {
        id                <- (j --\ "ID").as[String]
        address           <- (j --\ "Address").as[String]
        enableTagOverride <- (j --\ "EnableTagOverride").as[Boolean]
        modifyIndex       <- (j --\ "ModifyIndex").as[Int]
        port              <- (j --\ "Port").as[Int]
        service           <- (j --\ "Service").as[String]
        tags              <- (j --\ "Tags").as[List[String]]
      } yield ServiceResponse(service, id, tags, address, port, enableTagOverride, modifyIndex)
    )
}
