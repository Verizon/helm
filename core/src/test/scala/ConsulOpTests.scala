package helm

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
}
