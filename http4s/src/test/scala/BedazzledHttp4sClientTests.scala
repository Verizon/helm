package consul.http4s

import BedazzledHttp4sClient._

import scalaz.{\/, Kleisli}
import scalaz.concurrent.Task
import scodec.bits.ByteVector
import scalaz.stream._
import org.http4s.{headers, EntityBody, Request, Response, Status}
import org.http4s.client.{Client, DisposableResponse, UnexpectedStatus}
import org.scalatest._, Matchers._
import org.scalactic.TypeCheckedTripleEquals

class BedazzledHttp4sClientTests extends FlatSpec with Matchers with TypeCheckedTripleEquals {
  import BedazzledHttp4sClientTests._

  it should "add the decoder Accept header" in {
    val client = Client(
      Kleisli{ req =>
        val accept = req.headers.get(headers.Accept).get
        val response = Response(status = Status.Ok, body = body(accept.toString))
        Task.now(DisposableResponse(response, Task.now(())))
      }, Task.now(()))
    client.expect[String](dummyRequest).attemptRun should ===(
      \/.right("Accept: text/*"))
  }
}

object BedazzledHttp4sClientTests {
  def constantResponseClient(response: Response): Client = {
    val dispResponse = DisposableResponse(response, Task.now(()))
    Client(Kleisli{req => Task.now(dispResponse)}, Task.now(()))
  }

  def body(s: String): EntityBody =
    Process.emit(ByteVector.encodeUtf8(s).right.get) // YOLO

  val dummyRequest: Request = Request()
}
