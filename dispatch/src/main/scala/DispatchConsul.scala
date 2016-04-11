package consul
package dispatch

import journal.Logger

import scalaz.{~>,\/}
import scalaz.concurrent.Task
import scalaz.syntax.std.option._
import scalaz.syntax.functor._
import scala.util.{Success,Failure}
import scala.concurrent.ExecutionContext
import argonaut._, Argonaut._
import _root_.dispatch._, _root_.dispatch.Defaults._

final class DispatchConsulClient(baseUri: Req,
                                 client: Http,
                                 executionContext: ExecutionContext,
                                 accessToken: Option[String] = None) extends (ConsulOp ~> Task) {
  private val log = Logger[this.type]

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

  def addToken(req: Req): Req = 
    accessToken.fold(req)(tok => req <:< Map("Consul-Token" -> tok))

  def set(key: Key, value: String): Task[Unit] = {
    val req = addToken((baseUri / key).PUT << value)

    for {
      _ <- Task.delay(log.debug(s"setting consul key $key to $value"))
      response <- fromScalaFuture(client(req))(executionContext)
      status = response.getStatusCode()
      body = if(response.hasResponseBody()) response.getResponseBody else ""
    } yield log.debug(s"setting consul key $key resulted in status: $status response: $body")
  }

  def get(key: Key): Task[String] =
    for {
      _ <- Task.delay(log.debug(s"fetching consul key $key"))
      res <- fromScalaFuture(client(addToken(baseUri / key)))(executionContext).map(_.getResponseBody)
      _ = log.debug(s"consul response for key $key: $res")
      decoded <- Parse.decodeEither[KvResponses](res).fold(e => Task.fail(new Exception(e)), Task.now)
      head <- keyValue(key, decoded)
    } yield {
      log.debug(s"consul value for key $key is $head")
      head.value
    }
}
