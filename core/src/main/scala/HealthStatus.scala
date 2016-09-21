package helm

import scalaz._, Scalaz._
import argonaut._, Argonaut._


sealed abstract class HealthStatus extends Product with Serializable

object HealthStatus {

  final case object Passing extends HealthStatus
  final case object Unknown extends HealthStatus
  final case object Warning extends HealthStatus
  final case object Critical extends HealthStatus

  def fromString(s: String): Option[HealthStatus] =
    s.toLowerCase match {
      case "passing"  => Some(Passing)
      case "warning"  => Some(Warning)
      case "critical" => Some(Critical)
      case "unknown"  => Some(Unknown)
      case _          => None
    }

  implicit val HealthStatusDecoder: DecodeJson[HealthStatus] =
    DecodeJson[HealthStatus] { c =>
      (c --\ "Status").as[String].flatMap { s =>
        fromString(s).cata(
          some = r => DecodeResult.ok(r),
          none = DecodeResult.fail(s"invalid health status: $s", c.history)
        )
      }
    }
}
