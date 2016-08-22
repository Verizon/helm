package helm
package http4s

import scalaz.{\/, ~>, Kleisli}
import scalaz.concurrent.Task
import scalaz.stream.Process
import scodec.bits.ByteVector
import org.http4s.{EntityBody, Request, Response, Status, Uri}
import org.http4s.client._
import org.scalatest._, Matchers._
import org.scalactic.TypeCheckedTripleEquals

class Http4sConsulTests extends FlatSpec with Matchers with TypeCheckedTripleEquals {
  import Http4sConsulTests._

  "get" should "succeed with some when the response is 200" in {
    val response = consulResponse(Status.Ok, "yay")
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.get("foo")).attemptRun should ===(
      \/.right(Some("yay")))
  }

  "get" should "succeed with none when the response is 404" in {
    val response = consulResponse(Status.NotFound, "nope")
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.get("foo")).attemptRun should ===(
      \/.right(None))
  }

  it should "fail when the response is 500" in {
    val response = consulResponse(Status.InternalServerError, "boo")
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.get("foo")).attemptRun should ===(
      \/.left(UnexpectedStatus(Status.InternalServerError)))
  }

  "set" should "succeed when the response is 200" in {
    val response = consulResponse(Status.Ok, "yay")
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.set("foo", "bar")).attemptRun should ===(
      \/.right(()))
  }

  it should "fail when the response is 500" in {
    val response = consulResponse(Status.InternalServerError, "boo")
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.set("foo", "bar")).attemptRun should ===(
      \/.left(UnexpectedStatus(Status.InternalServerError)))
  }
}

object Http4sConsulTests {
  private val base64Encoder = java.util.Base64.getEncoder

  def constantConsul(response: Response): ConsulOp ~> Task = {
    new Http4sConsulClient(
      Uri.uri("http://localhost:8500/v1/kv/v1"),
      constantResponseClient(response),
      None)
  }

  def consulResponse(status: Status, s: String): Response = {
    val base64 = new String(base64Encoder.encode(s.getBytes("utf-8")), "utf-8")
    val responseBody = body(s)
    Response(status = status, body = responseBody)
  }

  def constantResponseClient(response: Response): Client = {
    val dispResponse = DisposableResponse(response, Task.now(()))
    Client(Kleisli{req => Task.now(dispResponse)}, Task.now(()))
  }

  def body(s: String): EntityBody =
    Process.emit(ByteVector.encodeUtf8(s).right.get) // YOLO

  val dummyRequest: Request = Request()
}
