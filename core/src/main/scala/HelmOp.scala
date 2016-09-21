package helm

import scala.collection.immutable.{Set => SSet}
import scala.language.existentials
import scalaz.{\/, Coyoneda, EitherT, Free, Monad}
import scalaz.std.option._
import scalaz.std.vector._
import scalaz.syntax.traverse._
import argonaut.{DecodeJson, EncodeJson, StringWrap}, StringWrap.StringToParseWrap


sealed abstract class ConsulOp[A] extends Product with Serializable

object ConsulOp {

  final case class Get(key: Key) extends ConsulOp[Option[String]]

  final case class Set(key: Key, value: String) extends ConsulOp[Unit]

  final case class Delete(key: Key) extends ConsulOp[Unit]

  final case class ListKeys(prefix: Key) extends ConsulOp[SSet[String]]

  final case class HealthCheck(service: String) extends ConsulOp[String]

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

  def healthCheckJson[A:DecodeJson](service: String): ConsulOpF[Err \/ SSet[A]] =
    healthCheck(service).map(_.decodeEither[SSet[A]])
}

