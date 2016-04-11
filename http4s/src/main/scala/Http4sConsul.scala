package consul
package http4s

import journal.Logger
import BedazzledHttp4sClient._

import org.http4s._
import org.http4s.client._
import org.http4s.argonaut.jsonOf
import scalaz.~>
import scalaz.concurrent.Task
import scalaz.stream.Process
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

  def addHeader(req: Request): Request =
    accessToken.fold(req)(tok => req.putHeaders(Header("X-Consul-Token", tok)))

  def get(key: Key): Task[String] = {
    for {
      _ <- Task.delay(log.debug(s"fetching consul key $key"))
      kvs <- client.expect[KvResponses](addHeader(Request(uri = baseUri / "v1" / "kv" / key)))
      head <- keyValue(key, kvs)
    } yield {
      log.debug(s"consul value for key $key is $kvs")
      head.value
    }
  }

  def set(key: Key, value: String): Task[Unit]=
    for {
      _ <- Task.delay(log.debug(s"setting consul key $key to $value"))
      response <- client.expect[String](
        addHeader(
          Request(
            uri = baseUri / "v1" / "kv" / key,
            body = Process.emit(ByteVector.view(value.getBytes("UTF-8"))))))
    } yield log.debug(s"setting consul key $key resulted in response $response")
}
