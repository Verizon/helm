package helm

import argonaut._, Argonaut._

sealed abstract class Interval

/** Some types to represent check intervals, et al. */
object Interval {

  // So I considered using Scala's FiniteDuration type, but it
  // seems like overkill.
  // Also note that Consul supports intervals like "2h45m",
  // seemingly because Go's time formatting library does,
  // but that doesn't seem useful in a health check interval.
  final case class Nanoseconds(value: Double) extends Interval
  final case class Microseconds(value: Double) extends Interval
  final case class Milliseconds(value: Double) extends Interval
  final case class Seconds(value: Double) extends Interval
  final case class Minutes(value: Double) extends Interval
  final case class Hours(value: Double) extends Interval

  def toString(i: Interval): String = {
    i match {
      case Nanoseconds(v)  => f"${v}%fns"
      case Microseconds(v) => f"${v}%fus"
      case Milliseconds(v) => f"${v}%fms"
      case Seconds(v)      => f"${v}%fs"
      case Minutes(v)      => f"${v}%fm"
      case Hours(v)        => f"${v}%fh"
    }
  }

  implicit val IntervalEncoder: EncodeJson[Interval] =
    EncodeJson[Interval] { i => jString(toString(i)) }
}
