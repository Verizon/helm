import argonaut._, Argonaut._
import scalaz.{~>,Free,Monad}

package object helm {
  type Err = String // YOLO
  type Key = String

  def run[F[_]:Monad, A](interpreter: ConsulOp ~> F, op: ConsulOp.ConsulOpF[A]): F[A] =
    Free.runFC(op)(interpreter)
}
