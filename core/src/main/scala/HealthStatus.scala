package helm

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

  def toString(hs: HealthStatus): String =
    hs match {
      case Passing  => "passing"
      case Warning  => "warning"
      case Critical => "critical"
      case Unknown  => "unknown"
    }

  implicit val HealthStatusDecoder: DecodeJson[HealthStatus] =
    DecodeJson[HealthStatus] { c =>
      c.as[String].flatMap { s =>
        fromString(s) match {
          case Some(r) => DecodeResult.ok(r)
          case None => DecodeResult.fail(s"invalid health status: $s", c.history)
        }
      }
    }

  implicit val HealthStatusEncoder: EncodeJson[HealthStatus] =
    EncodeJson[HealthStatus] { hs => jString(toString(hs)) }
}
