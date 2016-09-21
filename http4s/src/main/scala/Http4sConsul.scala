package helm
package http4s

import journal.Logger

import argonaut.DecodeJson
import org.http4s._
import org.http4s.client._
import org.http4s.argonaut.jsonOf
import org.http4s.headers.Authorization
import org.http4s.Status.{Ok, NotFound}
import scalaz.~>
import scalaz.concurrent.Task
import scalaz.stream.Process
import scodec.bits.ByteVector
import scala.collection.immutable.{Set => SSet}

final class Http4sConsulClient(baseUri: Uri,
                               client: Client,
                               accessToken: Option[String] = None,
                               credentials: Option[(String,String)] = None) extends (ConsulOp ~> Task) {

  private implicit val keysDecoder: EntityDecoder[List[String]] = jsonOf[List[String]]

  private val log = Logger[this.type]

  def apply[A](op: ConsulOp[A]): Task[A] = op match {
    case ConsulOp.Get(key)             => get(key)
    case ConsulOp.Set(key, value)      => set(key, value)
    case ConsulOp.ListKeys(prefix)     => list(prefix)
    case ConsulOp.Delete(key)          => delete(key)
    case ConsulOp.HealthCheck(service) => healthCheck(service)
  }

  def addHeader(req: Request): Request =
    accessToken.fold(req)(tok => req.putHeaders(Header("X-Consul-Token", tok)))

  def addCreds(req: Request): Request =
    credentials.fold(req){case (un,pw) => req.putHeaders(Authorization(BasicCredentials(un,pw)))}

  def get(key: Key): Task[Option[String]] = {
    for {
      _ <- Task.delay(log.debug(s"fetching consul key $key"))
      req = Request(uri = (baseUri / "v1" / "kv" / key).+?("raw"))
      value <- client.expect[String](req).map(Some.apply).handleWith {
        case UnexpectedStatus(NotFound) => Task.now(None)
      }
    } yield {
      log.debug(s"consul value for key $key is $value")
      value
    }
  }

  def set(key: Key, value: String): Task[Unit] =
    for {
      _ <- Task.delay(log.debug(s"setting consul key $key to $value"))
      response <- client.expect[String](
        addCreds(addHeader(
          Request(Method.PUT,
            uri = baseUri / "v1" / "kv" / key,
            body = Process.emit(ByteVector.view(value.getBytes("UTF-8")))))))
    } yield log.debug(s"setting consul key $key resulted in response $response")

  def list(prefix: Key): Task[Set[Key]] = {
    val req = addCreds(addHeader(Request(uri = (baseUri / "v1" / "kv" / prefix).withQueryParam(QueryParam.fromKey("keys")))))

    for {
      _ <- Task.delay(log.debug(s"listing key consul with the prefix: $prefix"))
      response <- client.expect[List[String]](req)
    } yield {
      log.debug(s"listing of keys: " + response)
      response.toSet
    }
  }

  def delete(key: Key): Task[Unit] = {
    val req = addCreds(addHeader(Request(Method.DELETE, uri = (baseUri / "v1" / "kv" / key))))

    for {
      _ <- Task.delay(log.debug(s"deleting $key from the consul KV store"))
      response <- client.expect[String](req)
    } yield log.debug(s"response from delete: " + response)
  }

  def healthCheck(service: String): Task[String] = {
    import org.http4s.argonaut._
    for {
      _ <- Task.delay(log.debug(s"fetching health status for $service"))
      req = Request(uri = (baseUri / "v1" / "health" / "checks" / service))
      response <- client.expect[String](req)
    } yield {
      log.debug(s"health check response: " + response)
      response
    }
  }
}
