import argonaut._, Argonaut._
import cats.{~>,Monad}
import cats.free.Free

package object helm {
  type Err = String // YOLO
  type Key = String

  def run[F[_]:Monad, A](interpreter: ConsulOp ~> F, op: ConsulOp.ConsulOpF[A]): F[A] =
    op.foldMap(interpreter)
}
