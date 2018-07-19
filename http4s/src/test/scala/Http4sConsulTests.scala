package helm
package http4s

import cats.~>
import cats.data.Kleisli
import cats.effect.IO
import fs2.{Chunk, Stream}
import org.http4s.{EntityBody, Header, Headers, Request, Response, Status, Uri}
import org.http4s.client._
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest._
import org.scalatest.matchers.{BeMatcher, MatchResult}
import org.http4s.syntax.string.http4sStringSyntax
import scala.reflect.ClassTag

class Http4sConsulTests extends FlatSpec with Matchers with TypeCheckedTripleEquals {
  import Http4sConsulTests._

  "kvGetRaw" should "succeed with some when the response is 200" in {
    val response = consulResponse(Status.Ok, "yay", consulHeaders(999, false, 1))
    val csl = constantConsul(response)
    // Equality comparison for Option[Array[Byte]] doesn't work properly, but it should be representable as a String so this should be okay
    helm.run(csl, ConsulOp.kvGetRaw("foo", None, None)).attempt.unsafeRunSync.right.map(r => r.copy(value = r.value.map(new String(_)))) should ===(
      Right(QueryResponse[Option[String]](Some("yay"), 999, false, 1)))
  }

  "kvGetRaw" should "succeed with none when the response is 404" in {
    val response = consulResponse(Status.NotFound, "nope", consulHeaders(999, false, 1))
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.kvGetRaw("foo", None, None)).attempt.unsafeRunSync should ===(
      Right(QueryResponse[Option[Array[Byte]]](None, 999, false, 1)))
  }

  "kvGetRaw" should "succeed with none when the response body is empty" in {
    val response = consulResponse(Status.Ok, "", consulHeaders(999, false, 1))
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.kvGetRaw("foo", None, None)).attempt.unsafeRunSync should ===(
      Right(QueryResponse[Option[Array[Byte]]](None, 999, false, 1)))
  }

  it should "fail when the response is 500" in {
    val response = consulResponse(Status.InternalServerError, "error")
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.kvGetRaw("foo", None, None)).attempt.unsafeRunSync should be (consulErrorException)
  }

  "kvGet" should "succeed with some when the response is 200" in {
    val response = consulResponse(Status.Ok, kvGetReplyJson, consulHeaders(555, false, 2))
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.kvGet("foo", Some(true), None, None, None, None)).attempt.unsafeRunSync should ===(
      Right(kvGetReturnValue))
  }

  "kvGet" should "succeed with empty list when the response is 404" in {
    val response = consulResponse(Status.NotFound, "nope", consulHeaders(555, false, 2))
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.kvGet("foo", None, None, None, None, None)).attempt.unsafeRunSync should ===(
      Right(QueryResponse(List.empty[KVGetResult], 555, false, 2)))
  }

  it should "fail when the response is 500" in {
    val response = consulResponse(Status.InternalServerError, "error")
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.kvGet("foo", None, None, None, None, None)).attempt.unsafeRunSync should be (consulErrorException)
  }

  "kvSet" should "succeed when the response is 200" in {
    val response = consulResponse(Status.Ok, "yay")
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.kvSet("foo", "bar".getBytes)).attempt.unsafeRunSync should ===(
      Right(()))
  }

  it should "fail when the response is 500" in {
    val response = consulResponse(Status.InternalServerError, "error")
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.kvSet("foo", "bar".getBytes)).attempt.unsafeRunSync should be (consulErrorException)
  }

  "healthListChecksForNode" should "succeed with the proper result when the response is 200" in {
    val response = consulResponse(Status.Ok, serviceHealthChecksReplyJson, consulHeaders(1234, true, 0))
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.healthListChecksForNode("localhost", None, None, None)).attempt.unsafeRunSync should ===(
      Right(healthStatusResponse))
  }

  it should "fail when the response is 500" in {
    val response = consulResponse(Status.InternalServerError, "error", consulHeaders(1234, true, 0))
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.healthListChecksForNode("localhost", None, None, None)).attempt.unsafeRunSync should be (consulErrorException)
  }

  "healthListChecksInState" should "succeed with the proper result when the response is 200" in {
    val response = consulResponse(Status.Ok, serviceHealthChecksReplyJson, consulHeaders(1234, true, 0))
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.healthListChecksInState(HealthStatus.Passing, None, None, None, None, None)).attempt.unsafeRunSync should ===(
      Right(healthStatusResponse))
  }

  it should "fail when the response is 500" in {
    val response = consulResponse(Status.InternalServerError, "error", consulHeaders(1234, true, 0))
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.healthListChecksInState(HealthStatus.Passing, None, None, None, None, None)).attempt.unsafeRunSync should be (consulErrorException)
  }

  "healthListChecksForService" should "succeed with the proper result when the response is 200" in {
    val response = consulResponse(Status.Ok, serviceHealthChecksReplyJson, consulHeaders(1234, true, 0))
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.healthListChecksForService("test", None, None, None, None, None)).attempt.unsafeRunSync should ===(
      Right(healthStatusResponse))
  }

  it should "fail when the response is 500" in {
    val response = consulResponse(Status.InternalServerError, "error", consulHeaders(1234, true, 0))
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.healthListChecksForService("test", None, None, None, None, None)).attempt.unsafeRunSync should be (consulErrorException)
  }

  "healthListNodesForService" should "succeed with the proper result when the response is 200" in {
    val response = consulResponse(Status.Ok, healthNodesForServiceReplyJson, consulHeaders(1234, true, 0))
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.healthListNodesForService("test", None, None, None, None, None, None, None)).attempt.unsafeRunSync should ===(
      Right(healthNodesForServiceReturnValue))
  }

  "healthListNodesForService" should "fail if X-Consul-Index is omitted" in {
    val response = consulResponse(Status.Ok, healthNodesForServiceReplyJson, consulHeaders(1234, true, 0).filter(_.name != "X-Consul-Index".ci))
    val csl = constantConsul(response)

    helm.run(csl, ConsulOp.healthListNodesForService("test", None, None, None, None, None, None, None)).attempt.unsafeRunSync should be (
      consulHeaderException("Header not present in response: X-Consul-Index"))
  }

  "healthListNodesForService" should "fail if X-Consul-KnownLeader is omitted" in {
    val response = consulResponse(Status.Ok, healthNodesForServiceReplyJson, consulHeaders(1234, true, 0).filter(_.name != "X-Consul-KnownLeader".ci))
    val csl = constantConsul(response)

    helm.run(csl, ConsulOp.healthListNodesForService("test", None, None, None, None, None, None, None)).attempt.unsafeRunSync should be (
      consulHeaderException("Header not present in response: X-Consul-KnownLeader"))
  }

  "healthListNodesForService" should "fail if X-Consul-LastContact is omitted" in {
    val response = consulResponse(Status.Ok, healthNodesForServiceReplyJson, consulHeaders(1234, true, 0).filter(_.name != "X-Consul-LastContact".ci))
    val csl = constantConsul(response)

    helm.run(csl, ConsulOp.healthListNodesForService("test", None, None, None, None, None, None, None)).attempt.unsafeRunSync should be (
      consulHeaderException("Header not present in response: X-Consul-LastContact"))
  }

  it should "fail when the response is 500" in {
    val response = consulResponse(Status.InternalServerError, "error", consulHeaders(1234, true, 0))
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.healthListNodesForService("test", None, None, None, None, None, None, None)).attempt.unsafeRunSync should be (consulErrorException)
  }

  "agentRegisterService" should "succeed when the response is 200" in {
    val response = consulResponse(Status.Ok, "yay")
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.agentRegisterService("testService", Some("testId"), None, None, None, None, None, None)).attempt.unsafeRunSync should ===(
      Right(()))
  }

  it should "fail when the response is 500" in {
    val response = consulResponse(Status.InternalServerError, "error")
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.agentRegisterService("testService", Some("testId"), None, None, None, None, None, None)).attempt.unsafeRunSync should be (consulErrorException)
  }

  "agentDeregisterService" should "succeed when the response is 200" in {
    val response = consulResponse(Status.Ok, "")
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.agentDeregisterService("testService")).attempt.unsafeRunSync should ===(
      Right(()))
  }

  it should "fail when the response is 500" in {
    val response = consulResponse(Status.InternalServerError, "error")
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.agentDeregisterService("testService")).attempt.unsafeRunSync should be (consulErrorException)
  }

  "agentEnableMaintenanceMode" should "succeed when the response is 200" in {
    val response = consulResponse(Status.Ok, "")
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.agentEnableMaintenanceMode("testService", true, None)).attempt.unsafeRunSync should ===(
      Right(()))
  }

  it should "fail when the response is 500" in {
    val response = consulResponse(Status.InternalServerError, "error")
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.agentEnableMaintenanceMode("testService", true, None)).attempt.unsafeRunSync should be (consulErrorException)
  }

  "agentListServices" should "succeed with the proper result when the response is 200" in {
    val response = consulResponse(Status.Ok, dummyServicesReply)
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.agentListServices).attempt.unsafeRunSync should ===(
      Right(
        Map(
          "consul" -> ServiceResponse("consul", "consul", List.empty, "", 8300, false, 1L, 2L),
          "test"   -> ServiceResponse("testService", "test", List("testTag", "anotherTag"), "127.0.0.1", 1234, false, 123455121300L, 123455121321L)
        )
      ))
  }

  it should "fail when the response is 500" in {
    val response = consulResponse(Status.InternalServerError, "error")
    val csl = constantConsul(response)
    helm.run(csl, ConsulOp.agentListServices).attempt.unsafeRunSync should be (consulErrorException)
  }
}

object Http4sConsulTests {
  def constantConsul(response: Response[IO]): ConsulOp ~> IO = {
    new Http4sConsulClient(
      Uri.uri("http://localhost:8500/v1/kv/v1"),
      constantResponseClient(response),
      None)
  }

  def consulHeaders(index: Long, knownLeader: Boolean, lastContact: Long): Headers = {
    Headers(
      List(
        Header.Raw("X-Consul-Index".ci, index.toString),
        Header.Raw("X-Consul-KnownLeader".ci, knownLeader.toString),
        Header.Raw("X-Consul-LastContact".ci, lastContact.toString)
      )
    )
  }

  def consulResponse(status: Status, s: String, headers: Headers = Headers.empty): Response[IO] = {
    val responseBody = body(s)
    Response(status = status, body = responseBody, headers = headers)
  }

  def constantResponseClient(response: Response[IO]): Client[IO] = {
    val dispResponse = DisposableResponse(response, IO.unit)
    Client(Kleisli{req => IO.pure(dispResponse)}, IO.unit)
  }

  def body(s: String): EntityBody[IO] =
    Stream.chunk(Chunk.bytes(s.getBytes("UTF-8"))) // YOLO

  val dummyRequest: Request[IO] = Request[IO]()

  val dummyServicesReply = """
  {
      "consul": {
          "Address": "",
          "CreateIndex": 1,
          "EnableTagOverride": false,
          "ID": "consul",
          "ModifyIndex": 2,
          "Port": 8300,
          "Service": "consul",
          "Tags": []
      },
      "test": {
          "Address": "127.0.0.1",
          "CreateIndex": 123455121300,
          "EnableTagOverride": false,
          "ID": "test",
          "ModifyIndex": 123455121321,
          "Port": 1234,
          "Service": "testService",
          "Tags": [
              "testTag",
              "anotherTag"
          ]
      }
  }
  """

  val serviceHealthChecksReplyJson = """
  [
      {
          "CheckID": "service:testService",
          "CreateIndex": 19008,
          "ModifyIndex": 19013,
          "Name": "Service 'testService' check",
          "Node": "localhost",
          "Notes": "test note",
          "Output": "HTTP GET https://test.test.test/: 200 OK Output: all's well",
          "ServiceID": "testServiceID",
          "ServiceName": "testServiceName",
          "ServiceTags": ["testTag"],
          "Status": "passing"
      },
      {
          "CheckID": "service:testService#2",
          "CreateIndex": 123455121300,
          "ModifyIndex": 123455121321,
          "Name": "other check",
          "Node": "localhost",
          "Notes": "a note",
          "Output": "Get https://test.test.test/: dial tcp 192.168.1.71:443: getsockopt: connection refused",
          "ServiceID": "testServiceID",
          "ServiceName": "testServiceName",
          "ServiceTags": ["testTag", "anotherTag"],
          "Status": "critical"
      }
  ]
  """


  val healthNodesForServiceReplyJson = """
  [
      {
          "Checks": [
              {
                  "CheckID": "service:testService",
                  "CreateIndex": 19008,
                  "ModifyIndex": 19013,
                  "Name": "Service 'testService' check",
                  "Node": "localhost",
                  "Notes": "test note",
                  "Output": "HTTP GET https://test.test.test/: 200 OK Output: all's well",
                  "ServiceID": "testServiceID",
                  "ServiceName": "testServiceName",
                  "ServiceTags": ["testTag"],
                  "Status": "passing"
              },
              {
                  "CheckID": "service:testService#2",
                  "CreateIndex": 123455121300,
                  "ModifyIndex": 123455121321,
                  "Name": "other check",
                  "Node": "localhost",
                  "Notes": "a note",
                  "Output": "Get https://test.test.test/: dial tcp 192.168.1.71:443: getsockopt: connection refused",
                  "ServiceID": "testServiceID",
                  "ServiceName": "testServiceName",
                  "ServiceTags": ["testTag", "anotherTag"],
                  "Status": "critical"
              }
          ],
          "Node": {
              "Address": "192.168.1.145",
              "CreateIndex": 123455121311,
              "Datacenter": "dc1",
              "ID": "cb1f6030-a220-4f92-57dc-7baaabdc3823",
              "Meta": {
                "metaTest": "test123"
              },
              "ModifyIndex": 123455121347,
              "Node": "localhost",
              "TaggedAddresses": {
                  "lan": "192.168.1.145",
                  "wan": "192.168.2.145"
              }
          },
          "Service": {
              "Address": "127.0.0.1",
              "CreateIndex": 123455121301,
              "EnableTagOverride": false,
              "ID": "test",
              "ModifyIndex": 123455121322,
              "Port": 1234,
              "Service": "testService",
              "Tags": [
                  "testTag",
                  "anotherTag"
              ]
          }
      }
  ]
  """

  val kvGetReplyJson = """
  [
      {
          "Key": "foo",
          "Value": null,
          "Flags": 0,
          "LockIndex": 0,
          "CreateIndex": 43788,
          "ModifyIndex": 43789
      },
      {
          "Key": "foo/baz",
          "Value": "cXV4",
          "Flags": 1234,
          "Session": "adf4238a-882b-9ddc-4a9d-5b6758e4159e",
          "LockIndex": 0,
          "CreateIndex": 43790,
          "ModifyIndex": 43791
      }
  ]
  """

  val kvGetReturnValue =
    QueryResponse(
      List(
        KVGetResult("foo", None, 0, None, 0, 43788, 43789),
        KVGetResult("foo/baz", Some("cXV4"), 1234, Some("adf4238a-882b-9ddc-4a9d-5b6758e4159e"), 0, 43790, 43791)
      ),
      555,
      false,
      2
    )

  val healthNodesForServiceReturnValue =
    QueryResponse(
      List(
        HealthNodesForServiceResponse(
          NodeResponse(
            "cb1f6030-a220-4f92-57dc-7baaabdc3823",
            "localhost",
            "192.168.1.145",
            "dc1",
            Map("metaTest" -> "test123"),
            TaggedAddresses("192.168.1.145", "192.168.2.145"),
            123455121311L,
            123455121347L
          ),
          ServiceResponse(
            "testService",
            "test",
            List("testTag",
            "anotherTag"),
            "127.0.0.1",
            1234,
            false,
            123455121301L,
            123455121322L
          ),
          List(
            HealthCheckResponse(
              "localhost",
              "service:testService",
              "Service 'testService' check",
              HealthStatus.Passing,
              "test note",
              "HTTP GET https://test.test.test/: 200 OK Output: all's well",
              "testServiceID",
              "testServiceName",
              List("testTag"),
              19008L,
              19013L),
            HealthCheckResponse(
              "localhost",
              "service:testService#2",
              "other check",
              HealthStatus.Critical,
              "a note",
              "Get https://test.test.test/: dial tcp 192.168.1.71:443: getsockopt: connection refused",
              "testServiceID",
              "testServiceName",
              List("testTag", "anotherTag"),
              123455121300L,
              123455121321L)
          )
        )
      ),
      1234,
      true,
      0
    )

  val healthStatusResponse =
    QueryResponse(
      List(
        HealthCheckResponse(
          "localhost",
          "service:testService",
          "Service 'testService' check",
          HealthStatus.Passing,
          "test note",
          "HTTP GET https://test.test.test/: 200 OK Output: all's well",
          "testServiceID",
          "testServiceName",
          List("testTag"),
          19008L,
          19013L),
        HealthCheckResponse(
          "localhost",
          "service:testService#2",
          "other check",
          HealthStatus.Critical,
          "a note",
          "Get https://test.test.test/: dial tcp 192.168.1.71:443: getsockopt: connection refused",
          "testServiceID",
          "testServiceName",
          List("testTag", "anotherTag"),
          123455121300L,
          123455121321L)
      ),
      1234,
      true,
      0
    )

  // Some custom matchers here because ScalaTest's built-in matching doesn't handle Left(Throwable) well.
  // It has handling for thrown exceptions, but not just straight-up comparison.
  // Who knows, maybe I missed something and this is just redundant. Ah well.

  class LeftExceptionMatcher[E <: Exception: ClassTag](exception: E) extends BeMatcher[Either[Throwable, _]] {
    val expectedExceptionType = exception.getClass.getName
    val expectedMessage = exception.getMessage
    def apply(e: Either[Throwable, _]) =
      e match {
        case l@Left(e: E) if e.getMessage == expectedMessage => MatchResult(true, s"$l was $expectedExceptionType($expectedMessage)", s"$l was not $expectedExceptionType($expectedMessage)")
        case other => MatchResult(false, s"Expected Left($expectedExceptionType($expectedMessage)), but got $other", s"Expected something that WASN'T Left($expectedExceptionType($expectedMessage)), but that's what we got")
      }
  }

  def leftException(exception: Exception) = new LeftExceptionMatcher(exception)

  def consulHeaderException(message: String) = leftException(new NoSuchElementException(message))

  val consulErrorException = leftException(new RuntimeException("Got error response from Consul: error"))
}
