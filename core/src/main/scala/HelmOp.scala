package helm

import scala.collection.immutable.{Set => SSet}
import scala.language.existentials
import scalaz.{\/, Coyoneda, EitherT, Free, Monad}
import scalaz.std.option._
import scalaz.syntax.traverse._
import argonaut.{DecodeJson, EncodeJson, StringWrap}, StringWrap.StringToParseWrap

sealed abstract class HelmOp[A] extends Product with Serializable

object HelmOp {

  final case class Get(key: Key) extends HelmOp[Option[String]]

  final case class Set(key: Key, value: String) extends HelmOp[Unit]

  final case class Delete(key: Key) extends HelmOp[Unit]

  final case class ListKeys(prefix: Key) extends HelmOp[SSet[String]]

  type HelmOpF[A] = Free.FreeC[HelmOp, A]
  type HelmOpC[A] = Coyoneda[HelmOp, A]

  // this shouldn't be necessary, but we need to help the compiler out a bit
  implicit val HelmOpFMonad: Monad[HelmOpF] = Free.freeMonad[HelmOpC]

  def get(key: Key): HelmOpF[Option[String]] =
    Free.liftFC(Get(key))

  def getJson[A:DecodeJson](key: Key): HelmOpF[Err \/ Option[A]] =
    get(key).map(_.traverseU(_.decodeEither[A]))

  def set(key: Key, value: String): HelmOpF[Unit] =
    Free.liftFC(Set(key, value))

  def setJson[A](key: Key, value: A)(implicit A: EncodeJson[A]): HelmOpF[Unit] =
    set(key, A.encode(value).toString)

  def delete(key: Key): HelmOpF[Unit] = 
    Free.liftFC(Delete(key))

  def listKeys(prefix: Key): HelmOpF[SSet[String]] =
    Free.liftFC(ListKeys(prefix))
}
