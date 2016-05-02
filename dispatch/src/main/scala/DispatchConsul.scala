package consul
package dispatch

import journal.Logger
import delorean._

import scalaz.{~>,\/}
import scalaz.concurrent.{Strategy, Task}
import scalaz.syntax.std.option._
import scalaz.syntax.functor._
import scala.util.{Success,Failure}
import scala.concurrent.ExecutionContext
import argonaut._, Argonaut._
import _root_.dispatch._, _root_.dispatch.Defaults._

final class DispatchConsulClient(baseUri: Req,
                                 client: Http,
                                 executionContext: ExecutionContext,
                                 strategy: Strategy,
                                 accessToken: Option[String] = None,
                                 credentials: Option[(String,String)] = None) extends (ConsulOp ~> Task) {
  private val log = Logger[this.type]

  implicitly[DecodeJson[KvResponse]]
  implicitly[DecodeJson[KvResponses]]

  def apply[A](op: ConsulOp[A]): Task[A] = op match {
    case ConsulOp.Get(key) => get(key)
    case ConsulOp.Set(key, value) => set(key, value)
    case ConsulOp.ListKeys(prefix) => list(prefix)
    case ConsulOp.Delete(key) => delete(key)
  }

  def addToken(req: Req): Req = 
    accessToken.fold(req)(tok => req <:< Map("X-Consul-Token" -> tok))

  def addCredentials(req: Req): Req = 
    credentials.fold(req){case (un,pw) => req.as_!(un, pw)}

  def set(key: Key, value: String): Task[Unit] = {
    val req = addCredentials(addToken((baseUri / "v1" / "kv" / key).PUT << value))

    for {
      _ <- Task.delay(log.debug(s"setting consul key $key to $value"))
      response <- client(req).toTask(executionContext, strategy)
      status = response.getStatusCode()
      body = if(response.hasResponseBody()) response.getResponseBody else ""
    } yield log.debug(s"setting consul key $key resulted in status: $status response: $body")
  }

  def get(key: Key): Task[String] =
    for {
      _ <- Task.delay(log.debug(s"fetching consul key $key"))
      res <- client(addCredentials(addToken(baseUri / "v1" / "kv" / key))).toTask(executionContext, strategy).map(_.getResponseBody)
      _ = log.debug(s"consul response for key $key: $res")
      decoded <- Parse.decodeEither[KvResponses](res).fold(e => Task.fail(new Exception(e)), Task.now)
      head <- keyValue(key, decoded)
    } yield {
      log.debug(s"consul value for key $key is $head")
      head.value
    }

  def delete(key: Key): Task[Unit] = {
    val req = addCredentials(addToken((baseUri / "v1" / "kv" / key).DELETE))
    for {
      _ <- Task.delay(log.debug(s"deleting $key from consul"))
      response <- client(req).toTask(executionContext, strategy)
      status = response.getStatusCode()
      body = if(response.hasResponseBody()) response.getResponseBody else ""
    } yield log.debug(s"deleting $key from consul resulted in status: $status response: $body")
  }



  def list(prefix: Key): Task[Set[String]] =
    for {
      _ <- Task.delay(log.debug(s"fetching list of consul keys prefixed by $prefix"))
      res <- client(addCredentials(addToken(baseUri / "v1" / "kv" / prefix <<? Map("keys" -> "")))).toTask.map(_.getResponseBody)
      decoded <- Parse.decodeEither[List[String]](res).fold(e => Task.fail(new Exception(e)), Task.now)
    } yield {
      decoded.toSet
    }
}
