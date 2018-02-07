package helm

import argonaut._, Argonaut._
import cats.effect.IO
import org.scalatest.{FlatSpec, Matchers}
import org.scalactic.TypeCheckedTripleEquals
import ConsulOp._

class ConsulOpTests extends FlatSpec with Matchers with TypeCheckedTripleEquals {
  val I = Interpreter.prepare[ConsulOp, IO]

  "getJson" should "return none right when get returns None" in {
    val interp = for {
      _ <- I.expectU[Option[String]] {
        case ConsulOp.KVGet("foo") => IO.pure(None)
      }
    } yield ()
    interp.run(kvGetJson[Json]("foo")).unsafeRunSync should equal(Right(None))
  }

  it should "return a value when get returns a decodeable value" in {
    val interp = for {
      _ <- I.expectU[Option[String]] {
        case ConsulOp.KVGet("foo") => IO.pure(Some("42"))
      }
    } yield ()
    interp.run(kvGetJson[Json]("foo")).unsafeRunSync should equal(Right(Some(jNumber(42))))
  }

  it should "return an error when get returns a non-decodeable value" in {
    val interp = for {
      _ <- I.expectU[Option[String]] {
        case ConsulOp.KVGet("foo") => IO.pure(Some("{"))
      }
    } yield ()
    interp.run(kvGetJson[Json]("foo")).unsafeRunSync should equal(Left("JSON terminates unexpectedly."))
  }
}

