package consul

import scala.collection.immutable.{Set => SSet}
import scala.language.existentials
import scalaz.{\/, Coyoneda, EitherT, Free, Monad}
import argonaut.{DecodeJson, EncodeJson, StringWrap}, StringWrap.StringToParseWrap

sealed abstract class ConsulOp[A] extends Product with Serializable

object ConsulOp {

  final case class Get(key: Key) extends ConsulOp[String]

  final case class Set(key: Key, value: String) extends ConsulOp[Unit]

  final case class ListKeys(prefix: Key) extends ConsulOp[SSet[String]]

  type ConsulOpF[A] = Free.FreeC[ConsulOp, A]
  type ConsulOpC[A] = Coyoneda[ConsulOp, A]

  // this shouldn't be necessary, but we need to help the compiler out a bit
  implicit val consulOpFMonad: Monad[ConsulOpF] = Free.freeMonad[ConsulOpC]

  def get(key: Key): ConsulOpF[String] =
    Free.liftFC(Get(key))

  def getJson[A:DecodeJson](key: Key): EitherT[ConsulOpF, Err, A] =
    EitherT[ConsulOpF, Err, A](get(key).map(_.decodeEither[A]))

  def set(key: Key, value: String): ConsulOpF[Unit] =
    Free.liftFC(Set(key, value))

  def setJson[A](key: Key, value: A)(implicit A: EncodeJson[A]): ConsulOpF[Unit] =
    set(key, A.encode(value).toString)

  def listKeys(prefix: Key): ConsulOpF[SSet[String]] =
    Free.liftFC(ListKeys(prefix))
}
