package consul
package http4s

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

final class Http4sConsulClient(baseUri: Uri, client: Client) extends (ConsulOp ~> Task) {
  private implicit val responseDecoder: EntityDecoder[KvResponses] = jsonOf[KvResponses]

  def apply[A](op: ConsulOp[A]): Task[A] = op match {
    case ConsulOp.Get(key) => get(key)
    case ConsulOp.Set(key, value) => set(key, value)
  }

  def get(key: Key): Task[String] =
    for {
      kvs <- client.getAs[KvResponses](baseUri / key)
      head <- keyValue(key, kvs)
    } yield head.value

  def set(key: Key, value: String): Task[Unit]=
    client.fetchAs[String](PUT((baseUri / key), ByteVector(value.getBytes("UTF-8")))).void
}
