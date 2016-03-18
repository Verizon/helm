package consul

import scala.language.existentials
import scalaz.{\/, Free}
import argonaut.{DecodeJson, EncodeJson, StringWrap}, StringWrap.StringToParseWrap

sealed abstract class ConsulOp[A] extends Product with Serializable

object ConsulOp {

  final case class Get(key: Key) extends ConsulOp[String]

  final case class Set(key: Key, value: String) extends ConsulOp[Unit]

  type ConsulOpF[A] = Free.FreeC[ConsulOp, A]

  def get(key: Key): ConsulOpF[String] =
    Free.liftFC(Get(key))

  def getJson[A:DecodeJson](key: Key): ConsulOpF[Err \/ A] =
    get(key).map(_.decodeEither[A])

  def set(key: Key, value: String): ConsulOpF[Unit] =
    Free.liftFC(Set(key, value))

  def setJson[A](key: Key, value: A)(implicit A: EncodeJson[A]): ConsulOpF[Unit] = ???
}
