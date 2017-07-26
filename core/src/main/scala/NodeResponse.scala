package helm

import argonaut._, Argonaut._

/** Case class representing a health check as returned from an API call to Consul */
final case class NodeResponse(
  id:              String,
  node:            String,
  address:         String,
  datacenter:      String,
  meta:            Map[String, String],
  taggedAddresses: TaggedAddresses,
  createIndex:     Long,
  modifyIndex:     Long
)

object NodeResponse {
  implicit def NodeResponseDecoder: DecodeJson[NodeResponse] =
    DecodeJson(j =>
      for {
        id              <- (j --\ "ID").as[String]
        node            <- (j --\ "Node").as[String]
        address         <- (j --\ "Address").as[String]
        datacenter      <- (j --\ "Datacenter").as[String]
        meta            <- (j --\ "Meta").as[Map[String, String]]
        taggedAddresses <- (j --\ "TaggedAddresses").as[TaggedAddresses]
        createIndex     <- (j --\ "CreateIndex").as[Long]
        modifyIndex     <- (j --\ "ModifyIndex").as[Long]
      } yield NodeResponse(
        id,
        node,
        address,
        datacenter,
        meta,
        taggedAddresses,
        createIndex,
        modifyIndex)
    )
}

final case class TaggedAddresses(lan: String, wan: String)

object TaggedAddresses {
  implicit def TaggedAddressesDecoder: DecodeJson[TaggedAddresses] =
    DecodeJson(j =>
      for {
        lan <- (j --\ "lan").as[String]
        wan <- (j --\ "wan").as[String]
      } yield TaggedAddresses(lan, wan)
    )
}
