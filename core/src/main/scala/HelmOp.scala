package helm

import scala.collection.immutable.{Set => SSet}
import scala.language.existentials
import scalaz.{\/, Coyoneda, EitherT, Free, Monad, NonEmptyList}
import scalaz.std.option._
import scalaz.syntax.traverse._
import argonaut.{DecodeJson, EncodeJson, StringWrap}, StringWrap.StringToParseWrap

sealed abstract class ConsulOp[A] extends Product with Serializable

object ConsulOp {

  final case class Get(key: Key) extends ConsulOp[Option[String]]

  final case class Set(key: Key, value: String) extends ConsulOp[Unit]

  final case class Delete(key: Key) extends ConsulOp[Unit]

  final case class ListKeys(prefix: Key) extends ConsulOp[SSet[String]]

  final case class HealthCheck(service: String) extends ConsulOp[String]

  final case object AgentListServices extends ConsulOp[Map[String, ServiceResponse]]

  final case class AgentRegisterService(
    service: String,
    id:      Option[String],
    tags:    Option[NonEmptyList[String]],
    address: Option[String],
    port:    Option[Int]
  ) extends ConsulOp[Unit]

  final case class AgentDeregisterService(id: String) extends ConsulOp[Unit]

  final case class AgentEnableMaintenanceMode(id: String, enable: Boolean, reason: Option[String]) extends ConsulOp[Unit]

  type ConsulOpF[A] = Free.FreeC[ConsulOp, A]
  type ConsulOpC[A] = Coyoneda[ConsulOp, A]

  // this shouldn't be necessary, but we need to help the compiler out a bit
  implicit val ConsulOpFMonad: Monad[ConsulOpF] = Free.freeMonad[ConsulOpC]

  def get(key: Key): ConsulOpF[Option[String]] =
    Free.liftFC(Get(key))

  def getJson[A:DecodeJson](key: Key): ConsulOpF[Err \/ Option[A]] =
    get(key).map(_.traverseU(_.decodeEither[A]))

  def set(key: Key, value: String): ConsulOpF[Unit] =
    Free.liftFC(Set(key, value))

  def setJson[A](key: Key, value: A)(implicit A: EncodeJson[A]): ConsulOpF[Unit] =
    set(key, A.encode(value).toString)

  def delete(key: Key): ConsulOpF[Unit] =
    Free.liftFC(Delete(key))

  def listKeys(prefix: Key): ConsulOpF[SSet[String]] =
    Free.liftFC(ListKeys(prefix))

  def healthCheck(service: String): ConsulOpF[String] =
    Free.liftFC(HealthCheck(service))

  def healthCheckJson[A:DecodeJson](service: String): ConsulOpF[Err \/ List[A]] =
    healthCheck(service).map(_.decodeEither[List[A]])

  def agentListServices(): ConsulOpF[Map[String, ServiceResponse]] =
    Free.liftFC(AgentListServices)

  def agentRegisterService(
    service: String,
    id:      Option[String],
    tags:    Option[NonEmptyList[String]],
    address: Option[String],
    port:    Option[Int]
  ): ConsulOpF[Unit] =
    Free.liftFC(AgentRegisterService(service, id, tags, address, port))

  def agentDeregisterService(id: String): ConsulOpF[Unit] =
    Free.liftFC(AgentDeregisterService(id))

  def agentEnableMaintenanceMode(id: String, enable: Boolean, reason: Option[String]): ConsulOpF[Unit] =
    Free.liftFC(AgentEnableMaintenanceMode(id, enable, reason))

}
