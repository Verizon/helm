package helm
package http4s

import argonaut.Json
import argonaut.Json.jEmptyObject
import argonaut.StringWrap.StringToStringWrap
import cats.data.NonEmptyList
import cats.effect.Effect
import cats.~>
import cats.implicits._
import journal.Logger
import org.http4s.Method.PUT
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
  private implicit val listKvGetResultDecoder: EntityDecoder[F, List[KVGetResult]] = jsonOf[F, List[KVGetResult]]
  private implicit val listServicesDecoder: EntityDecoder[F, Map[String, ServiceResponse]] = jsonOf[F, Map[String, ServiceResponse]]
  private implicit val listHealthChecksDecoder: EntityDecoder[F, List[HealthCheckResponse]] = jsonOf[F, List[HealthCheckResponse]]
  private implicit val listHealthNodesForServiceResponseDecoder: EntityDecoder[F, List[HealthNodesForServiceResponse]] =
    jsonOf[F, List[HealthNodesForServiceResponse]]

  private val log = Logger[this.type]

  def apply[A](op: ConsulOp[A]): F[A] = op match {
    case ConsulOp.KVGet(key, recurse, datacenter, separator, index, wait) =>
      kvGet(key, recurse, datacenter, separator, index, wait)
    case ConsulOp.KVGetRaw(key, index, wait) => kvGetRaw(key, index, wait)
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

  /** A nice place to store the Consul response headers so we can pass them around */
  case class ConsulHeaders(
    index:       Long,
    lastContact: Long,
    knownLeader: Boolean
  )

  /** Helper function to get the value of a header out of a Response */
  def extractHeaderValue(header: String, response: Response[F]): F[String] = {
    response.headers.get(header.ci) match {
      case Some(header) => F.pure(header.value)
      case None         => F.pure(new RuntimeException(s"Header not present in response: $header")).flatMap(F.raiseError)
    }
  }

  /** Helper function to get Consul GET request metadata from response headers */
  def extractConsulHeaders(response: Response[F]): F[ConsulHeaders] = {
    for {
      index       <- extractHeaderValue("X-Consul-Index", response).map(_.toLong)
      lastContact <- extractHeaderValue("X-Consul-LastContact", response).map(_.toLong)
      knownLeader <- extractHeaderValue("X-Consul-KnownLeader", response).map(_.toBoolean)
    } yield ConsulHeaders(index, lastContact, knownLeader)
  }

  /**
    * Encapsulates the functionality for parsing out the Consul headers from the HTTP response and decoding the JSON body.
    * Note: these headers are only present for a portion of the API.
    */
  def extractQueryResponse[A](response: Response[F])(implicit d: EntityDecoder[F, A]): F[QueryResponse[A]] = response match {
    case Successful(r) =>
      for {
        headers     <- extractConsulHeaders(response)
        decodedBody <- d.decode(r, strict = false).fold(throw _, identity)
      } yield {
        QueryResponse(decodedBody, headers.index, headers.knownLeader, headers.lastContact)
      }
    case failedResponse =>
      F.pure(UnexpectedStatus(failedResponse.status)).flatMap(F.raiseError)
  }

  def kvGet(
    key:        Key,
    recurse:    Option[Boolean],
    datacenter: Option[String],
    separator:  Option[String],
    index:      Option[Long],
    wait:       Option[Interval]
  ): F[QueryResponse[List[KVGetResult]]] = {
    for {
      _ <- F.delay(log.debug(s"fetching consul key $key"))
      req = addCreds(addConsulToken(
        Request(
          uri =
            (baseUri / "v1" / "kv" / key)
              .+??("recurse", recurse)
              .+??("dc", datacenter)
              .+??("separator", separator)
              .+??("index", index)
              .+??("wait", wait.map(Interval.toString)))))
      response <- client.fetch[QueryResponse[List[KVGetResult]]](req) { response: Response[F] =>
        response.status match {
          case status@(Status.Ok|Status.NotFound) =>
            for {
              headers <- extractConsulHeaders(response)
              value   <- if (status == Status.Ok) listKvGetResultDecoder.decode(response, strict = false).fold(throw _, identity) else F.pure(List.empty)
            } yield {
              QueryResponse(value, headers.index, headers.knownLeader, headers.lastContact)
            }
          case status =>
            F.pure(UnexpectedStatus(status)).flatMap(F.raiseError)
        }
      }
    } yield {
      log.debug(s"consul response for key get $key was $response")
      response
    }
  }

  def kvGetRaw(
    key:   Key,
    index: Option[Long],
    wait:  Option[Interval]
  ): F[QueryResponse[Option[Array[Byte]]]] = {
    for {
      _ <- F.delay(log.debug(s"fetching consul key $key"))
      req = addCreds(addConsulToken(
        Request(
          uri =
            (baseUri / "v1" / "kv" / key)
              .+?("raw")
              .+??("index", index)
              .+??("wait", wait.map(Interval.toString)))))
      response <- client.fetch[QueryResponse[Option[Array[Byte]]]](req) { response: Response[F] =>
        response.status match {
          case status@(Status.Ok|Status.NotFound) =>
            for {
              headers <- extractConsulHeaders(response)
              value   <- if (status == Status.Ok) response.body.compile.toVector.map(vec => Some(vec.toArray)) else F.pure(None)
            } yield {
              QueryResponse(value, headers.index, headers.knownLeader, headers.lastContact)
            }
          case status =>
            F.pure(UnexpectedStatus(status)).flatMap(F.raiseError)
        }
      }
    } yield {
      log.debug(s"consul response for raw key get $key is $response")
      response
    }
  }

  def kvSet(key: Key, value: Array[Byte]): F[Unit] =
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
}
