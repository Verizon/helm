package helm

import scala.collection.immutable.{Set => SSet}
import argonaut._, Argonaut._
import scalaz.\/
import scalaz.concurrent.Task
import scalaz.concurrent.Task.{delay, now}
import org.scalatest.{FlatSpec, Matchers}
import org.scalactic.TypeCheckedTripleEquals
import ConsulOp._

class ConsulOpTests extends FlatSpec with Matchers with TypeCheckedTripleEquals {
  val I = Interpreter.prepare[ConsulOp, Task]

  "getJson" should "return none right when get returns None" in {
    val interp = for {
      _ <- I.expectU[Option[String]] {
        case ConsulOp.Get("foo") => now(None)
      }
    } yield ()
    interp.run(getJson[Json]("foo")).run should equal(\/.right(None))
  }

  it should "return a value when get returns a decodeable value" in {
    val interp = for {
      _ <- I.expectU[Option[String]] {
        case ConsulOp.Get("foo") => now(Some("42"))
      }
    } yield ()
    interp.run(getJson[Json]("foo")).run should equal(\/.right(Some(jNumber(42))))
  }

  it should "return an error when get returns a non-decodeable value" in {
    val interp = for {
      _ <- I.expectU[Option[String]] {
        case ConsulOp.Get("foo") => now(Some("{"))
      }
    } yield ()
    interp.run(getJson[Json]("foo")).run should equal(\/.left("JSON terminates unexpectedly."))
  }

  "healthCheck" should "return a vector of health status values when decodeable" in {
    import HealthStatus._
    val interp = for {
      _ <- I.expectU[String] {
        case ConsulOp.HealthCheck("foo") => now("""[{"Status":"passing"},{"Status":"warning"}]""")
      }
    } yield ()
    interp.run(healthCheckJson[HealthStatus]("foo")).run should equal(\/.right(SSet(Passing,Warning)))
  }

  it should "return a error if status id not decodeable" in {
    import HealthStatus._
    val interp = for {
      _ <- I.expectU[String] {
        case ConsulOp.HealthCheck("foo") => now("""[{"Status":"bar"}]""")
      }
    } yield ()
    interp.run(healthCheckJson[HealthStatus]("foo")).run.isLeft should equal(true)
  }

  it should "return a empty set if response is empty" in {
    import HealthStatus._
    val interp = for {
      _ <- I.expectU[String] {
        case ConsulOp.HealthCheck("foo") => now("""[]""")
      }
    } yield ()
    interp.run(healthCheckJson[HealthStatus]("foo")).run should equal(\/.right(SSet()))
  }
}
