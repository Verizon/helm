package helm

import argonaut._, Argonaut._

/** Case class representing the response to a KV "Read Key" API call to Consul */
final case class KVGetResult(
  key:         String,
  value:       String,
  flags:       Long,
  session:     Option[String],
  lockIndex:   Long,
  createIndex: Long,
  modifyIndex: Long
)

object KVGetResult {
  implicit def KVGetResultDecoder: DecodeJson[KVGetResult] =
    DecodeJson(j =>
      for {
        key         <- (j --\ "Key").as[String]
        value       <- (j --\ "Value").as[String]
        flags       <- (j --\ "Flags").as[Long]
        session     <- (j --\ "Session").as[Option[String]]
        lockIndex   <- (j --\ "LockIndex").as[Long]
        createIndex <- (j --\ "CreateIndex").as[Long]
        modifyIndex <- (j --\ "ModifyIndex").as[Long]
      } yield KVGetResult(
        key,
        value,
        flags,
        session,
        lockIndex,
        createIndex,
        modifyIndex)
    )
}
