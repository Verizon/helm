package consul.http4s

import scalaz.concurrent.Task
import org.http4s.headers.Accept
import org.http4s.{DecodeFailureException, EntityDecoder, Request, Response, Status}
import org.http4s.client.Client
import scala.util.control.NoStackTrace

final case class BedazzledHttp4sClient(client: Client) extends AnyVal {
  import BedazzledHttp4sClient._

  def expect[A](req: Request)(implicit d: EntityDecoder[A]): Task[A] = {
    val request = putHeaders(req, d)
    client.fetch(request) { resp =>
      for {
        succ <- if (resp.status.isSuccess) Task.now(resp) else Task.fail(NonSuccessResponse(resp.status))
        decodeResult <- d.decode(succ, strict = false).run
        decoded <- Task.fromDisjunction(decodeResult.leftMap(DecodeFailureException(_)))
      } yield decoded
    }
  }
}

object BedazzledHttp4sClient {
  def putHeaders(req: Request, decoder: EntityDecoder[_]): Request =
    if (decoder.consumes.nonEmpty) {
      val m = decoder.consumes.toList
      req.putHeaders(Accept(m.head, m.tail:_*))
    } else req

  final case class NonSuccessResponse(status: Status) extends RuntimeException with NoStackTrace {
    override def getMessage: String = s"request failed with status $status"
  }

  implicit def bedazzledHttp4sClient(client: Client): BedazzledHttp4sClient =
    new BedazzledHttp4sClient(client)
}
