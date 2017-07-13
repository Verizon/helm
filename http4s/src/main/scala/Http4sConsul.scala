package helm
package http4s

import journal.Logger

import argonaut.Json
import argonaut.Json.jEmptyObject
import argonaut.StringWrap.StringToStringWrap

import org.http4s._
import org.http4s.client._
import org.http4s.argonaut.jsonOf
import org.http4s.headers.Authorization
import org.http4s.Status.{Ok, NotFound}
import scalaz.{~>, NonEmptyList}
import scalaz.concurrent.Task
import scalaz.stream.Process
import scodec.bits.ByteVector
import scala.collection.immutable.{Set => SSet}

final class Http4sConsulClient(baseUri: Uri,
                               client: Client,
                               accessToken: Option[String] = None,
                               credentials: Option[(String,String)] = None) extends (ConsulOp ~> Task) {

  private implicit val keysDecoder: EntityDecoder[List[String]] = jsonOf[List[String]]
  private implicit val listServicesDecoder: EntityDecoder[Map[String, ServiceResponse]] = jsonOf[Map[String, ServiceResponse]]
  private implicit val listHealthChecksDecoder: EntityDecoder[List[HealthCheckResponse]] = jsonOf[List[HealthCheckResponse]]

  private val log = Logger[this.type]

  def apply[A](op: ConsulOp[A]): Task[A] = op match {
    case ConsulOp.Get(key)                        => get(key)
    case ConsulOp.Set(key, value)                 => set(key, value)
    case ConsulOp.ListKeys(prefix)                => list(prefix)
    case ConsulOp.Delete(key)                     => delete(key)
    case ConsulOp.ListHealthChecksForService(service, datacenter, near, nodeMeta) =>
      healthChecksForService(service, datacenter, near, nodeMeta)
    case ConsulOp.ListHealthChecksForNode(node, datacenter) =>
      healthChecksForNode(node, datacenter)
    case ConsulOp.AgentRegisterService(service, id, tags, address, port, enableTagOverride, check, checks) =>
      agentRegisterService(service, id, tags, address, port, enableTagOverride, check, checks)
    case ConsulOp.AgentDeregisterService(service) => agentDeregisterService(service)
    case ConsulOp.AgentListServices               => agentListServices()
    case ConsulOp.AgentEnableMaintenanceMode(id, enable, reason) =>
      agentEnableMaintenanceMode(id, enable, reason)
  }

  def addConsulToken(req: Request): Request =
    accessToken.fold(req)(tok => req.putHeaders(Header("X-Consul-Token", tok)))

  def addCreds(req: Request): Request =
    credentials.fold(req){case (un,pw) => req.putHeaders(Authorization(BasicCredentials(un,pw)))}

  def get(key: Key): Task[Option[String]] = {
    for {
      _ <- Task.delay(log.debug(s"fetching consul key $key"))
      req = addCreds(addConsulToken(Request(uri = (baseUri / "v1" / "kv" / key).+?("raw"))))
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
        addCreds(addConsulToken(
          Request(Method.PUT,
            uri = baseUri / "v1" / "kv" / key,
            body = Process.emit(ByteVector.view(value.getBytes("UTF-8")))))))
    } yield log.debug(s"setting consul key $key resulted in response $response")

  def list(prefix: Key): Task[Set[Key]] = {
    val req = addCreds(addConsulToken(Request(uri = (baseUri / "v1" / "kv" / prefix).withQueryParam(QueryParam.fromKey("keys")))))

    for {
      _ <- Task.delay(log.debug(s"listing key consul with the prefix: $prefix"))
      response <- client.expect[List[String]](req)
    } yield {
      log.debug(s"listing of keys: " + response)
      response.toSet
    }
  }

  def delete(key: Key): Task[Unit] = {
    val req = addCreds(addConsulToken(Request(Method.DELETE, uri = (baseUri / "v1" / "kv" / key))))

    for {
      _ <- Task.delay(log.debug(s"deleting $key from the consul KV store"))
      response <- client.expect[String](req)
    } yield log.debug(s"response from delete: " + response)
  }

  def healthChecksForService(
    service:    String,
    datacenter: Option[String],
    near:       Option[String],
    nodeMeta:   Option[String]
  ): Task[List[HealthCheckResponse]] = {
    for {
      _ <- Task.delay(log.debug(s"fetching health checks for service $service"))
      req = addCreds(addConsulToken(
        Request(
          uri = (baseUri / "v1" / "health" / "checks" / service).+??("dc", datacenter).+??("near", near).+??("node-meta", nodeMeta))))
      response <- client.expect[List[HealthCheckResponse]](req)
    } yield {
      log.debug(s"health check response: " + response)
      response
    }
  }

  def healthChecksForNode(
    node:       String,
    datacenter: Option[String]
  ): Task[List[HealthCheckResponse]] = {
    for {
      _ <- Task.delay(log.debug(s"fetching health checks for node $node"))
      req = addCreds(addConsulToken(
        Request(
          uri = (baseUri / "v1" / "health" / "node" / node).+??("dc", datacenter))))
      response <- client.expect[List[HealthCheckResponse]](req)
    } yield {
      log.debug(s"health check response: " + response)
      response
    }
  }

  def agentRegisterService(
    service:           String,
    id:                Option[String],
    tags:              Option[NonEmptyList[String]],
    address:           Option[String],
    port:              Option[Int],
    enableTagOverride: Option[Boolean],
    check:             Option[HealthCheckParameter],
    checks:            Option[NonEmptyList[HealthCheckParameter]]
  ): Task[Unit] = {
    val json: Json =
      ("Name"              :=  service)           ->:
      ("ID"                :=? id)                ->?:
      ("Tags"              :=? tags.map(_.list))  ->?:
      ("Address"           :=? address)           ->?:
      ("Port"              :=? port)              ->?:
      ("EnableTagOverride" :=? enableTagOverride) ->?:
      ("Check"             :=? check)             ->?:
      ("Checks"            :=? checks)            ->?:
      jEmptyObject

    for {
      _ <- Task.delay(log.debug(s"registering $service with json: ${json.toString}"))
      response <- client.expect[String](
        addCreds(addConsulToken(
          Request(Method.PUT,
            uri = baseUri / "v1" / "agent" / "service" / "register",
            body = Process.emit(ByteVector.view(json.toString.getBytes("UTF-8")))))))
    } yield log.debug(s"registering service $service resulted in response $response")
  }

  def agentDeregisterService(id: String): Task[Unit] = {
    val req = addCreds(addConsulToken(Request(Method.PUT, uri = (baseUri / "v1" / "agent" / "service" / "deregister" / id))))
    for {
      _ <- Task.delay(log.debug(s"deregistering service with id $id"))
      response <- client.expect[String](req)
    } yield log.debug(s"response from deregister: " + response)
  }

  def agentListServices(): Task[Map[String, ServiceResponse]] = {
    for {
      _ <- Task.delay(log.debug(s"listing services registered with local agent"))
      req = addCreds(addConsulToken(Request(uri = (baseUri / "v1" / "agent" / "services"))))
      services <- client.expect[Map[String, ServiceResponse]](req)
    } yield {
      log.debug(s"got services: $services")
      services
    }
  }

  def agentEnableMaintenanceMode(id: String, enable: Boolean, reason: Option[String]): Task[Unit] = {
    for {
      _ <- Task.delay(log.debug(s"setting service with id $id maintenance mode to $enable"))
      req = addCreds(addConsulToken(
        Request(Method.PUT,
          uri = (baseUri / "v1" / "agent" / "service" / "maintenance" / id).+?("enable", enable).+??("reason", reason))))
      response  <- client.expect[String](req)
    } yield log.debug(s"setting maintenance mode for service $id to $enable resulted in $response")
  }
}
