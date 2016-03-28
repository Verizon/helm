package consul.http4s

import BedazzledHttp4sClient._

import scalaz.{\/, Kleisli}
import scalaz.concurrent.Task
import scodec.bits.ByteVector
import scalaz.stream._
import org.http4s.{headers, EntityBody, Request, Response, Status}
import org.http4s.client.{Client, DisposableResponse}
import org.scalatest._, Matchers._
import org.scalactic.TypeCheckedTripleEquals

class BedazzledHttp4sClientTests extends FlatSpec with Matchers with TypeCheckedTripleEquals {
  import BedazzledHttp4sClientTests._

  "expect" should "return the body when the status is 200" in {
    val responseBody = body("yay")
    val response = Response(status = Status.Ok, body = responseBody)
    val client = constantResponseClient(response)
    client.expect[String](dummyRequest).attemptRun should ===(
      \/.right("yay"))
  }

  it should "return a failed future for a 500 response" in {
    val responseBody = body("ERROR!")
    val response = Response(status = Status.InternalServerError, body = responseBody)
    val client = constantResponseClient(response)
    client.expect[String](dummyRequest).attemptRun.leftMap{
      case NonSuccessResponse(req, res) => (res.status, res.body)
    } should ===(
      \/.left((Status.InternalServerError, responseBody)))
  }

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
