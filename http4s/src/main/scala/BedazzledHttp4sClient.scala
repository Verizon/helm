package consul.http4s

import scalaz.concurrent.Task
import org.http4s.headers.Accept
import org.http4s.{EntityDecoder, QValue, Request, Response, Status}
import org.http4s.client.Client
import scala.util.control.NoStackTrace

object BedazzledHttp4sClient {
  def putHeaders(req: Request, decoder: EntityDecoder[_]): Request =
    if (decoder.consumes.nonEmpty) {
      val m = decoder.consumes.map(_.withQValue(QValue.One)).toList
      req.putHeaders(Accept(m.head, m.tail:_*))
    } else req
}
