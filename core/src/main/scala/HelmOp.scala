package helm

import scala.collection.immutable.{Set => SSet}
import argonaut.{DecodeJson, EncodeJson, StringWrap}, StringWrap.StringToParseWrap
import cats.data.NonEmptyList
import cats.free.Free
import cats.free.Free.liftF
import cats.implicits._

sealed abstract class ConsulOp[A] extends Product with Serializable

object ConsulOp {

  final case class KVGet(key: Key) extends ConsulOp[Option[String]]

  final case class KVSet(key: Key, value: String) extends ConsulOp[Unit]

  final case class KVDelete(key: Key) extends ConsulOp[Unit]

  final case class KVListKeys(prefix: Key) extends ConsulOp[SSet[String]]

  final case class HealthListChecksForService(
    service:    String,
    datacenter: Option[String],
    near:       Option[String],
    nodeMeta:   Option[String],
    index:      Option[Long],
    maxWait:    Option[Interval]
  ) extends ConsulOp[QueryResponse[List[HealthCheckResponse]]]

  final case class HealthListChecksForNode(
    node:       String,
    datacenter: Option[String],
    index:      Option[Long],
    maxWait:    Option[Interval]
  ) extends ConsulOp[QueryResponse[List[HealthCheckResponse]]]

  final case class HealthListChecksInState(
    state:      HealthStatus,
    datacenter: Option[String],
    near:       Option[String],
    nodeMeta:   Option[String],
    index:      Option[Long],
    maxWait:    Option[Interval]
  ) extends ConsulOp[QueryResponse[List[HealthCheckResponse]]]

  // There's also a Catalog function called List Nodes for Service
  final case class HealthListNodesForService(
    service:     String,
    datacenter:  Option[String],
    near:        Option[String],
    nodeMeta:    Option[String],
    tag:         Option[String],
    passingOnly: Option[Boolean],
    index:       Option[Long],
    maxWait:     Option[Interval]
  ) extends ConsulOp[QueryResponse[List[HealthNodesForServiceResponse]]]

  final case object AgentListServices extends ConsulOp[Map[String, ServiceResponse]]

  final case class AgentRegisterService(
    service:           String,
    id:                Option[String],
    tags:              Option[NonEmptyList[String]],
    address:           Option[String],
    port:              Option[Int],
    enableTagOverride: Option[Boolean],
    check:             Option[HealthCheckParameter],
    checks:            Option[NonEmptyList[HealthCheckParameter]]
  ) extends ConsulOp[Unit]

  final case class AgentDeregisterService(id: String) extends ConsulOp[Unit]

  final case class AgentEnableMaintenanceMode(id: String, enable: Boolean, reason: Option[String]) extends ConsulOp[Unit]

  type ConsulOpF[A] = Free[ConsulOp, A]

  def kvGet(key: Key): ConsulOpF[Option[String]] =
    liftF(KVGet(key))

  def kvGetJson[A:DecodeJson](key: Key): ConsulOpF[Either[Err, Option[A]]] =
    kvGet(key).map(_.traverse(_.decodeEither[A]))

  def kvSet(key: Key, value: String): ConsulOpF[Unit] =
    liftF(KVSet(key, value))

  def kvSetJson[A](key: Key, value: A)(implicit A: EncodeJson[A]): ConsulOpF[Unit] =
    kvSet(key, A.encode(value).toString)

  def kvDelete(key: Key): ConsulOpF[Unit] =
    liftF(KVDelete(key))

  def kvListKeys(prefix: Key): ConsulOpF[SSet[String]] =
    liftF(KVListKeys(prefix))

  def healthListChecksForService(
    service:    String,
    datacenter: Option[String],
    near:       Option[String],
    nodeMeta:   Option[String],
    index:      Option[Long],
    maxWait:    Option[Interval]
  ): ConsulOpF[QueryResponse[List[HealthCheckResponse]]] =
    liftF(HealthListChecksForService(service, datacenter, near, nodeMeta, index, maxWait))

  def healthListChecksForNode(
    node:       String,
    datacenter: Option[String],
    index:      Option[Long],
    maxWait:    Option[Interval]
  ): ConsulOpF[QueryResponse[List[HealthCheckResponse]]] =
    liftF(HealthListChecksForNode(node, datacenter, index, maxWait))

  def healthListChecksInState(
    state:      HealthStatus,
    datacenter: Option[String],
    near:       Option[String],
    nodeMeta:   Option[String],
    index:      Option[Long],
    maxWait:    Option[Interval]
  ): ConsulOpF[QueryResponse[List[HealthCheckResponse]]] =
    liftF(HealthListChecksInState(state, datacenter, near, nodeMeta, index, maxWait))

  def healthListNodesForService(
    service:     String,
    datacenter:  Option[String],
    near:        Option[String],
    nodeMeta:    Option[String],
    tag:         Option[String],
    passingOnly: Option[Boolean],
    index:       Option[Long],
    maxWait:     Option[Interval]
  ): ConsulOpF[QueryResponse[List[HealthNodesForServiceResponse]]] =
    liftF(HealthListNodesForService(service, datacenter, near, nodeMeta, tag, passingOnly, index, maxWait))

  def agentListServices(): ConsulOpF[Map[String, ServiceResponse]] =
    liftF(AgentListServices)

  def agentRegisterService(
    service:           String,
    id:                Option[String],
    tags:              Option[NonEmptyList[String]],
    address:           Option[String],
    port:              Option[Int],
    enableTagOverride: Option[Boolean],
    check:             Option[HealthCheckParameter],
    checks:            Option[NonEmptyList[HealthCheckParameter]]
  ): ConsulOpF[Unit] =
    liftF(AgentRegisterService(service, id, tags, address, port, enableTagOverride, check, checks))

  def agentDeregisterService(id: String): ConsulOpF[Unit] =
    liftF(AgentDeregisterService(id))

  def agentEnableMaintenanceMode(id: String, enable: Boolean, reason: Option[String]): ConsulOpF[Unit] =
    liftF(AgentEnableMaintenanceMode(id, enable, reason))
}
