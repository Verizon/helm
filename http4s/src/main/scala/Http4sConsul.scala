package helm
package http4s

import journal.Logger

import argonaut.ArgonautCats._
import argonaut.Json
import argonaut.Json.jEmptyObject
import argonaut.StringWrap.StringToStringWrap

import org.http4s._
import org.http4s.client._
import org.http4s.argonaut._
import org.http4s.headers.Authorization
import org.http4s.Method.PUT
import org.http4s.Status.{Ok, NotFound}
import cats.~>
import cats.data.NonEmptyList
import fs2.Task
import scodec.bits.ByteVector
import scala.collection.immutable.{Set => SSet}

final class Http4sConsulClient(
  baseUri: Uri,
  client: Client,
  accessToken: Option[String] = None,
  credentials: Option[(String,String)] = None) extends (ConsulOp ~> Task) {

  private implicit val keysDecoder: EntityDecoder[List[String]] = jsonOf[List[String]]
  private implicit val listServicesDecoder: EntityDecoder[Map[String, ServiceResponse]] = jsonOf[Map[String, ServiceResponse]]
  private implicit val listHealthChecksDecoder: EntityDecoder[List[HealthCheckResponse]] = jsonOf[List[HealthCheckResponse]]
  private implicit val listHealthNodesForServiceResponseDecoder: EntityDecoder[List[HealthNodesForServiceResponse]] =
    jsonOf[List[HealthNodesForServiceResponse]]

  private val log = Logger[this.type]

  def apply[A](op: ConsulOp[A]): Task[A] = op match {
    case ConsulOp.KVGet(key)         => kvGet(key)
    case ConsulOp.KVSet(key, value)  => kvSet(key, value)
    case ConsulOp.KVListKeys(prefix) => kvList(prefix)
    case ConsulOp.KVDelete(key)      => kvDelete(key)
    case ConsulOp.HealthListChecksForService(service, datacenter, near, nodeMeta) =>
      healthChecksForService(service, datacenter, near, nodeMeta)
    case ConsulOp.HealthListChecksForNode(node, datacenter) =>
      healthChecksForNode(node, datacenter)
    case ConsulOp.HealthListChecksInState(state, datacenter, near, nodeMeta) =>
      healthChecksInState(state, datacenter, near, nodeMeta)
    case ConsulOp.HealthListNodesForService(service, datacenter, near, nodeMeta, tag, passingOnly) =>
      healthNodesForService(service, datacenter, near, nodeMeta, tag, passingOnly)
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

  def kvGet(key: Key): Task[Option[String]] = {
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

  def kvSet(key: Key, value: String): Task[Unit] =
    for {
      _ <- Task.delay(log.debug(s"setting consul key $key to $value"))
      req <- PUT(uri = baseUri / "v1" / "kv" / key, value).map(addConsulToken).map(addCreds)
      response <- client.expect[String](req)
    } yield log.debug(s"setting consul key $key resulted in response $response")

  def kvList(prefix: Key): Task[Set[Key]] = {
    val req = addCreds(addConsulToken(Request(uri = (baseUri / "v1" / "kv" / prefix).withQueryParam(QueryParam.fromKey("keys")))))

    for {
      _ <- Task.delay(log.debug(s"listing key consul with the prefix: $prefix"))
      response <- client.expect[List[String]](req)
    } yield {
      log.debug(s"listing of keys: " + response)
      response.toSet
    }
  }

  def kvDelete(key: Key): Task[Unit] = {
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

  def healthChecksInState(
    state:      HealthStatus,
    datacenter: Option[String],
    near:       Option[String],
    nodeMeta:   Option[String]
  ): Task[List[HealthCheckResponse]] = {
    for {
      _ <- Task.delay(log.debug(s"fetching health checks for service ${HealthStatus.toString(state)}"))
      req = addCreds(addConsulToken(
        Request(
          uri = (baseUri / "v1" / "health" / "state" / HealthStatus.toString(state)).+??("dc", datacenter).+??("near", near).+??("node-meta", nodeMeta))))
      response <- client.expect[List[HealthCheckResponse]](req)
    } yield {
      log.debug(s"health check response: " + response)
      response
    }
  }

  def healthNodesForService(
    service:     String,
    datacenter:  Option[String],
    near:        Option[String],
    nodeMeta:    Option[String],
    tag:         Option[String],
    passingOnly: Option[Boolean]
  ): Task[List[HealthNodesForServiceResponse]] = {
    for {
      _ <- Task.delay(log.debug(s"fetching nodes for service $service from health API"))
      req = addCreds(addConsulToken(
        Request(
          uri =
            (baseUri / "v1" / "health" / "service" / service)
              .+??("dc", datacenter)
              .+??("near", near)
              .+??("node-meta", nodeMeta)
              .+??("tag", tag)
              .+??("passing", passingOnly.filter(identity))))) // all values of passing parameter are treated the same by Consul
      response <- client.expect[List[HealthNodesForServiceResponse]](req)
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
      ("Name"              :=  service)            ->:
      ("ID"                :=? id)                 ->?:
      ("Tags"              :=? tags.map(_.toList)) ->?:
      ("Address"           :=? address)            ->?:
      ("Port"              :=? port)               ->?:
      ("EnableTagOverride" :=? enableTagOverride)  ->?:
      ("Check"             :=? check)              ->?:
      ("Checks"            :=? checks)             ->?:
      jEmptyObject

    for {
      _ <- Task.delay(log.debug(s"registering $service with json: ${json.toString}"))
      req <- PUT(baseUri / "v1" / "agent" / "service" / "register", json).map(addConsulToken).map(addCreds)
      response <- client.expect[String](req)
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
