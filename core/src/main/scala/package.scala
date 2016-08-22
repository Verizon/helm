import argonaut._, Argonaut._
import scalaz.{~>,Free,Monad}
import scalaz.concurrent.Task
import scalaz.syntax.std.option._

package object helm {
  type Err = String // YOLO
  type Key = String

  def run[F[_]:Monad, A](interpreter: ConsulOp ~> F, op: ConsulOp.ConsulOpF[A]): F[A] =
    Free.runFC(op)(interpreter)

  private val base64Decoder = java.util.Base64.getDecoder
  private val base64StringDecoder: DecodeJson[String] =
    DecodeJson.optionDecoder(json =>
      if(json.isNull) {
        Some("")
      } else {
        json.string.flatMap(s => DecodeJson.tryTo(new String(base64Decoder.decode(s), "utf-8")))
      }
        , "base 64 string")

  private[helm] implicit val KvResponseDecoder: DecodeJson[KvResponse] =
    DecodeJson.jdecode1L(KvResponse.apply)("Value")(base64StringDecoder)

  private[helm] implicit val KvResponsesDecoder: DecodeJson[KvResponses] =
    implicitly[DecodeJson[List[KvResponse]]].map(KvResponses)

  private[helm] def keyValue(key: Key, responses: KvResponses): Task[KvResponse] =
    responses.values.headOption.cata(Task.now, Task.fail(new RuntimeException(s"no consul value for key $key")))

}
