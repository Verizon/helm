package helm
package http4s

import scala.concurrent.duration.DurationInt

import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import com.whisk.docker._
import com.whisk.docker.impl.dockerjava.{Docker, DockerJavaExecutorFactory}
import com.whisk.docker.scalatest._
import journal.Logger
import org.http4s._
import org.http4s.client._
import org.http4s.client.blaze._
import org.scalacheck._
import org.scalatest._
import org.scalatest.prop._

trait DockerConsulService extends DockerKit {
  private[this] val logger = Logger[DockerConsulService]

  override implicit val dockerFactory: DockerFactory = new DockerJavaExecutorFactory(
    new Docker(DefaultDockerClientConfig.createDefaultConfigBuilder().build(),
      factory = new NettyDockerCmdExecFactory()))

  val consulContainer = DockerContainer("progrium/consul")
    .withPorts(8500 -> Some(18512))
    .withCommand("-server", "-bootstrap")
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

  val client = PooledHttp1Client()

  // i'm expecting protocol://ip:port
  def parseDockerHost(url: String): Option[String] = {
    val parts = url.split(":")
    if(parts.length == 3)
      Some(parts(1).substring(2))
    else None
  }

  val baseUrl: Uri =
    (for {
      url <- sys.env.get("DOCKER_HOST")
      host <- parseDockerHost(url)
      uri <- Uri.fromString(s"http://$host:18512").toOption
    } yield uri).getOrElse(Uri.uri("http://127.0.0.1:18512"))

  val interpreter = new Http4sConsulClient(baseUrl, client)

  "consul" should "work" in check { (k: String, v: String) =>
    scala.concurrent.Await.result(dockerContainers.head.isReady(), 20.seconds)
    helm.run(interpreter, ConsulOp.set(k, v)).run
    helm.run(interpreter, ConsulOp.get(k)).run should be (Some(v))

    helm.run(interpreter, ConsulOp.listKeys("")).run should contain (k)
    helm.run(interpreter, ConsulOp.delete(k)).run
    helm.run(interpreter, ConsulOp.listKeys("")).run should not contain (k)
    true
  }(implicitly, implicitly, Arbitrary(Gen.alphaStr suchThat(_.size > 0)), implicitly, implicitly, Arbitrary(Gen.alphaStr), implicitly, implicitly)
}
