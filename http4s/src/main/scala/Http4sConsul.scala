package consul
package http4s

import journal.Logger

import org.http4s._
import org.http4s.dsl._
import org.http4s.client._
import org.http4s.argonaut.jsonOf
import org.http4s.dsl._
import scalaz.~>
import scalaz.concurrent.Task
import scalaz.syntax.std.option._
import scalaz.syntax.functor._

import scodec.bits.ByteVector

final class Http4sConsulClient(baseUri: Uri,
                               client: Client,
                               accessToken: Option[String] = None) extends (ConsulOp ~> Task) {
  private implicit val responseDecoder: EntityDecoder[KvResponses] = jsonOf[KvResponses]
  private val log = Logger[this.type]

  def apply[A](op: ConsulOp[A]): Task[A] = op match {
    case ConsulOp.Get(key) => get(key)
    case ConsulOp.Set(key, value) => set(key, value)
  }

  def addHeader(treq: Task[Request]): Task[Request] =
    treq.map(req => accessToken.fold(req)(tok => req.putHeaders(Header("Consul-Token", tok))))

  def get(key: Key): Task[String] = {
    for {
      _ <- Task.delay(log.debug(s"fetching consul key $key"))
      kvs <- client.fetchAs[KvResponses](addHeader(GET(baseUri / key)))
      head <- keyValue(key, kvs)
    } yield {
      log.debug(s"consul value for key $key is $kvs")
      head.value
    }
  }

  def set(key: Key, value: String): Task[Unit]=
    for {
      _ <- Task.delay(log.debug(s"setting consul key $key to $value"))
      response <- client.fetchAs[String](addHeader(PUT(baseUri / key, ByteVector.view(value.getBytes("UTF-8")))))
    } yield log.debug(s"setting consul key $key resulted in response $response")
}
