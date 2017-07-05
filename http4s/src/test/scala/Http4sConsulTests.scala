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

  "agentRegisterService" should "succeed when the response is 200" in {
    val response = consulResponse(Status.Ok, "yay")
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.agentRegisterService("testService", Some("testId"), None, None, None)).attemptRun should ===(
      \/.right(()))
  }

  it should "fail when the response is 500" in {
    val response = consulResponse(Status.InternalServerError, "boo")
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.agentRegisterService("testService", Some("testId"), None, None, None)).attemptRun should ===(
      \/.left(UnexpectedStatus(Status.InternalServerError)))
  }

  "agentDeregisterService" should "succeed when the response is 200" in {
    val response = consulResponse(Status.Ok, "")
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.agentDeregisterService("testService")).attemptRun should ===(
      \/.right(()))
  }

  it should "fail when the response is 500" in {
    val response = consulResponse(Status.InternalServerError, "")
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.agentDeregisterService("testService")).attemptRun should ===(
      \/.left(UnexpectedStatus(Status.InternalServerError)))
  }

  "agentListServices" should "succeed with the proper result when the response is 200" in {
    val response = consulResponse(Status.Ok, dummyServicesReply)
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.agentListServices).attemptRun should ===(
      \/.right(
        Map(
          "consul" -> ServiceResponse("consul", "consul", List.empty, "", 8300, false, 0),
          "test"   -> ServiceResponse("testService", "test", List("testTag"), "127.0.0.1", 1234, false, 0)
        )
      ))
  }

  it should "fail when the response is 500" in {
    val response = consulResponse(Status.InternalServerError, "boo")
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.agentListServices).attemptRun should ===(
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

  val dummyServicesReply = """
  {
      "consul": {
          "Address": "",
          "CreateIndex": 0,
          "EnableTagOverride": false,
          "ID": "consul",
          "ModifyIndex": 0,
          "Port": 8300,
          "Service": "consul",
          "Tags": []
      },
      "test": {
          "Address": "127.0.0.1",
          "CreateIndex": 0,
          "EnableTagOverride": false,
          "ID": "test",
          "ModifyIndex": 0,
          "Port": 1234,
          "Service": "testService",
          "Tags": [
              "testTag"
          ]
      }
  }
  """
}
