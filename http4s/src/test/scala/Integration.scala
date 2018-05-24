package helm
package http4s

import scala.concurrent.duration.DurationInt

import cats.effect.IO
import cats.implicits._
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import com.whisk.docker._
import com.whisk.docker.impl.dockerjava.{Docker, DockerJavaExecutorFactory}
import com.whisk.docker.scalatest._
import journal.Logger
import org.http4s._
import org.http4s.client.blaze._
import org.scalacheck._
import org.scalatest._
import org.scalatest.enablers.CheckerAsserting
import org.scalatest.prop._

// This is how we use docker-kit.  Nothing specific to helm in this trait.
trait DefaultDockerKit extends DockerKit {
  override implicit val dockerFactory: DockerFactory = new DockerJavaExecutorFactory(
    new Docker(DefaultDockerClientConfig.createDefaultConfigBuilder().build(),
      factory = new NettyDockerCmdExecFactory()))

  /** Get the docker host from the DOCKER_HOST environment variable, or 127.0.0.1 if undefined */
  lazy val dockerHost: String = {
    // i'm expecting protocol://ip:port
    sys.env.get("DOCKER_HOST").flatMap { url =>
      val parts = url.split(":")
      if (parts.length == 3)
        Some(parts(1).substring(2))
      else None
    }.getOrElse("127.0.0.1")
  }
}

trait DockerConsulService extends DefaultDockerKit {
  private[this] val logger = Logger[DockerConsulService]

  override implicit val dockerFactory: DockerFactory = new DockerJavaExecutorFactory(
    new Docker(DefaultDockerClientConfig.createDefaultConfigBuilder().build(),
      factory = new NettyDockerCmdExecFactory()))

  val ConsulPort = 18512

  val consulContainer =
    DockerContainer("consul:0.7.0", name = Some("consul"))
      .withPorts(8500 -> Some(ConsulPort))
      .withLogLineReceiver(LogLineReceiver(true, s => logger.debug(s"consul: $s")))
      .withReadyChecker(DockerReadyChecker.LogLineContains("agent: Synced"))

  abstract override def dockerContainers: List[DockerContainer] =
    consulContainer :: super.dockerContainers
}

class IntegrationSpec
    extends FlatSpec
    with Matchers
    with Checkers
    with BeforeAndAfterAll
    with DockerConsulService with DockerTestKit {

  val client = Http1Client[IO]().unsafeRunSync

  val baseUrl: Uri =
    Uri.fromString(s"http://${dockerHost}:${ConsulPort}").valueOr(throw _)

  val interpreter = new Http4sConsulClient(baseUrl, client)

  "consul" should "work" in check { (k: String, v: Array[Byte]) =>
    scala.concurrent.Await.result(dockerContainers.head.isReady(), 20.seconds)
    helm.run(interpreter, ConsulOp.kvSet(k, v)).unsafeRunSync
    // Equality comparison for Option[Array[Byte]] doesn't work properly. Since we expect the value to always be Some making a custom matcher doesn't seem worthwhile, so call .get on the Option
    // See https://github.com/scalatest/scalatest/issues/491
    helm.run(interpreter, ConsulOp.kvGetRaw(k, None, None)).unsafeRunSync.value.get should be (v)

    helm.run(interpreter, ConsulOp.kvListKeys("")).unsafeRunSync should contain (k)
    helm.run(interpreter, ConsulOp.kvDelete(k)).unsafeRunSync
    helm.run(interpreter, ConsulOp.kvListKeys("")).unsafeRunSync should not contain (k)
    true
  }(implicitly, implicitly, Arbitrary(Gen.alphaStr suchThat(_.size > 0)), implicitly, implicitly, Arbitrary.arbContainer[Array, Byte], implicitly, implicitly, implicitly[CheckerAsserting[EntityDecoder[IO, Array[Byte]]]], implicitly, implicitly)
}
