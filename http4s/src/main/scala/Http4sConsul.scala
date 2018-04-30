package helm
package http4s

import argonaut.Json
import argonaut.Json.jEmptyObject
import argonaut.StringWrap.StringToStringWrap
import cats.data.{EitherT, NonEmptyList}
import cats.effect.Effect
import cats.~>
import cats.implicits._
import journal.Logger
import org.http4s.Method.PUT
import org.http4s.Status.NotFound
import org.http4s._
import org.http4s.argonaut._
import org.http4s.client._
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.Authorization
import org.http4s.Status.Successful
import org.http4s.syntax.string.http4sStringSyntax

final class Http4sConsulClient[F[_]](
  baseUri: Uri,
  client: Client[F],
  accessToken: Option[String] = None,
  credentials: Option[(String,String)] = None)
  (implicit F: Effect[F]) extends (ConsulOp ~> F) {

  private[this] val dsl = new Http4sClientDsl[F]{}
  import dsl._

  private implicit val keysDecoder: EntityDecoder[F, List[String]] = jsonOf[F, List[String]]
  private implicit val listServicesDecoder: EntityDecoder[F, Map[String, ServiceResponse]] = jsonOf[F, Map[String, ServiceResponse]]
  private implicit val listHealthChecksDecoder: EntityDecoder[F, List[HealthCheckResponse]] = jsonOf[F, List[HealthCheckResponse]]
  private implicit val listHealthNodesForServiceResponseDecoder: EntityDecoder[F, List[HealthNodesForServiceResponse]] =
    jsonOf[F, List[HealthNodesForServiceResponse]]

  private val log = Logger[this.type]

  def apply[A](op: ConsulOp[A]): F[A] = op match {
    case ConsulOp.KVGet(key)         => kvGet(key)
    case ConsulOp.KVSet(key, value)  => kvSet(key, value)
    case ConsulOp.KVListKeys(prefix) => kvList(prefix)
    case ConsulOp.KVDelete(key)      => kvDelete(key)
    case ConsulOp.HealthListChecksForService(service, datacenter, near, nodeMeta, index, wait) =>
      healthChecksForService(service, datacenter, near, nodeMeta, index, wait)
    case ConsulOp.HealthListChecksForNode(node, datacenter, index, wait) =>
      healthChecksForNode(node, datacenter, index, wait)
    case ConsulOp.HealthListChecksInState(state, datacenter, near, nodeMeta, index, wait) =>
      healthChecksInState(state, datacenter, near, nodeMeta, index, wait)
    case ConsulOp.HealthListNodesForService(service, datacenter, near, nodeMeta, tag, passingOnly, index, wait) =>
      healthNodesForService(service, datacenter, near, nodeMeta, tag, passingOnly, index, wait)
    case ConsulOp.AgentRegisterService(service, id, tags, address, port, enableTagOverride, check, checks) =>
      agentRegisterService(service, id, tags, address, port, enableTagOverride, check, checks)
    case ConsulOp.AgentDeregisterService(service) => agentDeregisterService(service)
    case ConsulOp.AgentListServices               => agentListServices()
    case ConsulOp.AgentEnableMaintenanceMode(id, enable, reason) =>
      agentEnableMaintenanceMode(id, enable, reason)
  }

  def addConsulToken(req: Request[F]): Request[F] =
    accessToken.fold(req)(tok => req.putHeaders(Header("X-Consul-Token", tok)))

  def addCreds(req: Request[F]): Request[F] =
    credentials.fold(req){case (un,pw) => req.putHeaders(Authorization(BasicCredentials(un,pw)))}

  def kvGet(key: Key): F[Option[String]] = {
    for {
      _ <- F.delay(log.debug(s"fetching consul key $key"))
      req = addCreds(addConsulToken(Request(uri = (baseUri / "v1" / "kv" / key).+?("raw"))))
      value <- client.expect[String](req).map(Option.apply).recoverWith {
        case UnexpectedStatus(NotFound) => F.pure(None)
      }
    } yield {
      log.debug(s"consul value for key $key is $value")
      value
    }
  }

  def kvSet(key: Key, value: String): F[Unit] =
    for {
      _ <- F.delay(log.debug(s"setting consul key $key to $value"))
      req <- PUT(uri = baseUri / "v1" / "kv" / key, value).map(addConsulToken).map(addCreds)
      response <- client.expect[String](req)
    } yield log.debug(s"setting consul key $key resulted in response $response")

  def kvList(prefix: Key): F[Set[Key]] = {
    val req = addCreds(addConsulToken(Request(uri = (baseUri / "v1" / "kv" / prefix).withQueryParam(QueryParam.fromKey("keys")))))

    for {
      _ <- F.delay(log.debug(s"listing key consul with the prefix: $prefix"))
      response <- client.expect[List[String]](req)
    } yield {
      log.debug(s"listing of keys: $response")
      response.toSet
    }
  }

  def kvDelete(key: Key): F[Unit] = {
    val req = addCreds(addConsulToken(Request(Method.DELETE, uri = (baseUri / "v1" / "kv" / key))))

    for {
      _ <- F.delay(log.debug(s"deleting $key from the consul KV store"))
      response <- client.expect[String](req)
    } yield log.debug(s"response from delete: $response")
  }

  def healthChecksForService(
    service:    String,
    datacenter: Option[String],
    near:       Option[String],
    nodeMeta:   Option[String],
    index:      Option[Long],
    wait:       Option[Interval]
  ): F[QueryResponse[List[HealthCheckResponse]]] = {
    for {
      _ <- F.delay(log.debug(s"fetching health checks for service $service"))
      req = addCreds(addConsulToken(
        Request(
          uri =
            (baseUri / "v1" / "health" / "checks" / service)
              .+??("dc", datacenter)
              .+??("near", near)
              .+??("node-meta", nodeMeta)
              .+??("index", index)
              .+??("wait", wait.map(Interval.toString)))))
      response <- client.fetch[QueryResponse[List[HealthCheckResponse]]](req)(extractQueryResponse)
    } yield {
      log.debug(s"health check response: " + response)
      response
    }
  }

  def healthChecksForNode(
    node:       String,
    datacenter: Option[String],
    index:      Option[Long],
    wait:       Option[Interval]
  ): F[QueryResponse[List[HealthCheckResponse]]] = {
    for {
      _ <- F.delay(log.debug(s"fetching health checks for node $node"))
      req = addCreds(addConsulToken(
        Request(
          uri =
            (baseUri / "v1" / "health" / "node" / node)
              .+??("dc", datacenter)
              .+??("index", index)
              .+??("wait", wait.map(Interval.toString)))))
      response <- client.fetch[QueryResponse[List[HealthCheckResponse]]](req)(extractQueryResponse)
    } yield {
      log.debug(s"health checks for node response: $response")
      response
    }
  }

  def healthChecksInState(
    state:      HealthStatus,
    datacenter: Option[String],
    near:       Option[String],
    nodeMeta:   Option[String],
    index:      Option[Long],
    wait:       Option[Interval]
  ): F[QueryResponse[List[HealthCheckResponse]]] = {
    for {
      _ <- F.delay(log.debug(s"fetching health checks for service ${HealthStatus.toString(state)}"))
      req = addCreds(addConsulToken(
        Request(
          uri =
            (baseUri / "v1" / "health" / "state" / HealthStatus.toString(state))
              .+??("dc", datacenter)
              .+??("near", near)
              .+??("node-meta", nodeMeta)
              .+??("index", index)
              .+??("wait", wait.map(Interval.toString)))))
      response <- client.fetch[QueryResponse[List[HealthCheckResponse]]](req)(extractQueryResponse)
    } yield {
      log.debug(s"health checks in state response: $response")
      response
    }
  }

  def healthNodesForService(
    service:     String,
    datacenter:  Option[String],
    near:        Option[String],
    nodeMeta:    Option[String],
    tag:         Option[String],
    passingOnly: Option[Boolean],
    index:       Option[Long],
    wait:        Option[Interval]
  ): F[QueryResponse[List[HealthNodesForServiceResponse]]] = {
    for {
      _ <- F.delay(log.debug(s"fetching nodes for service $service from health API"))
      req = addCreds(addConsulToken(
        Request(
          uri =
            (baseUri / "v1" / "health" / "service" / service)
              .+??("dc", datacenter)
              .+??("near", near)
              .+??("node-meta", nodeMeta)
              .+??("tag", tag)
              .+??("passing", passingOnly.filter(identity)) // all values of passing parameter are treated the same by Consul
              .+??("index", index)
              .+??("wait", wait.map(Interval.toString)))))

      response <- client.fetch[QueryResponse[List[HealthNodesForServiceResponse]]](req)(extractQueryResponse)
    } yield {
      log.debug(s"health nodes for service response: $response")
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
  ): F[Unit] = {
    val json: Json =
      ("Name"              :=  service)              ->:
      ("ID"                :=? id)                   ->?:
      ("Tags"              :=? tags.map(_.toList))   ->?:
      ("Address"           :=? address)              ->?:
      ("Port"              :=? port)                 ->?:
      ("EnableTagOverride" :=? enableTagOverride)    ->?:
      ("Check"             :=? check)                ->?:
      ("Checks"            :=? checks.map(_.toList)) ->?:
      jEmptyObject

    for {
      _ <- F.delay(log.debug(s"registering $service with json: ${json.toString}"))
      req <- PUT(baseUri / "v1" / "agent" / "service" / "register", json).map(addConsulToken).map(addCreds)
      response <- client.expect[String](req)
    } yield log.debug(s"registering service $service resulted in response $response")
  }

  def agentDeregisterService(id: String): F[Unit] = {
    val req = addCreds(addConsulToken(Request(Method.PUT, uri = (baseUri / "v1" / "agent" / "service" / "deregister" / id))))
    for {
      _ <- F.delay(log.debug(s"deregistering service with id $id"))
      response <- client.expect[String](req)
    } yield log.debug(s"response from deregister: " + response)
  }

  def agentListServices(): F[Map[String, ServiceResponse]] = {
    for {
      _ <- F.delay(log.debug(s"listing services registered with local agent"))
      req = addCreds(addConsulToken(Request(uri = (baseUri / "v1" / "agent" / "services"))))
      services <- client.expect[Map[String, ServiceResponse]](req)
    } yield {
      log.debug(s"got services: $services")
      services
    }
  }

  def agentEnableMaintenanceMode(id: String, enable: Boolean, reason: Option[String]): F[Unit] = {
    for {
      _ <- F.delay(log.debug(s"setting service with id $id maintenance mode to $enable"))
      req = addCreds(addConsulToken(
        Request(Method.PUT,
          uri = (baseUri / "v1" / "agent" / "service" / "maintenance" / id).+?("enable", enable).+??("reason", reason))))
      response  <- client.expect[String](req)
    } yield log.debug(s"setting maintenance mode for service $id to $enable resulted in $response")
  }

  /**
    * Encapsulates the functionality for parsing out the Consul headers from the HTTP response and decoding the JSON body.
    * Note: these headers are only present for read endpoints, and only a subset of those.
    */
  private def extractQueryResponse[A](response: Response[F])(implicit d: EntityDecoder[F, A]): F[QueryResponse[A]] = response match {
    case Successful(r) =>
      val headers = r.headers
      (for {
        index         <- EitherT.fromOption[F](headers.get("X-Consul-Index".ci), "Header not present in response: X-Consul-Index").map(_.value.toLong)
        knownLeader   <- EitherT.fromOption[F](headers.get("X-Consul-KnownLeader".ci), "Header not present in response: X-Consul-KnownLeader").map(_.value.toBoolean)
        lastContact   <- EitherT.fromOption[F](headers.get("X-Consul-LastContact".ci), "Header not present in response: X-Consul-LastContact").map(_.value.toLong)
      } yield (index, knownLeader, lastContact)).fold(err => throw new RuntimeException(err), identity).flatMap {
        case (index, knownLeader, lastContact) =>
          d.decode(r, strict = false).fold(throw _, decoded => QueryResponse(decoded, index, knownLeader, lastContact))
      }
    case failedResponse =>
      F.pure(UnexpectedStatus(failedResponse.status)).flatMap(F.raiseError)
  }
}
