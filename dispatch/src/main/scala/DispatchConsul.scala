package consul
package dispatch

import scalaz.{~>,\/}
import scalaz.concurrent.Task
import scalaz.syntax.std.option._
import scalaz.syntax.functor._
import scala.util.{Success,Failure}
import scala.concurrent.ExecutionContext
import argonaut._, Argonaut._
import _root_.dispatch._, _root_.dispatch.Defaults._

final class DispatchConsulClient(baseUri: Req, client: Http, executionContext: ExecutionContext) extends (ConsulOp ~> Task) {

  implicitly[DecodeJson[KvResponse]]
  implicitly[DecodeJson[KvResponses]]

  def fromScalaFuture[A](a: Future[A])(e: ExecutionContext): Task[A] =
    Task async { k =>
      a.onComplete {
        case Success(t) => k(\/.right(t))
        case Failure(e) => k(\/.left(e))
      }
    }

  def apply[A](op: ConsulOp[A]): Task[A] = op match {
    case ConsulOp.Get(key) => get(key)
    case ConsulOp.Set(key, value) => set(key, value)
  }

  def set(key: Key, value: String): Task[Unit] = {
    val req = (baseUri / key).PUT << value
    fromScalaFuture(client(req))(executionContext).void
  }

  def get(key: Key): Task[String] =
    for {
      res <- fromScalaFuture(client(baseUri / key))(executionContext).map(_.getResponseBody)
      _ = println("json:" + res.toString)
      decoded <- Parse.decodeEither[KvResponses](res).fold(e => Task.fail(new Exception(e)), Task.now)
      head <- keyValue(key, decoded)

    } yield head.value
}
