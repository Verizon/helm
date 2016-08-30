package helm
package http4s

import com.whisk.docker._
import org.scalatest._
import org.scalatest.prop._
import org.http4s._
import org.http4s.client._
import org.http4s.client.blaze._
import scala.concurrent.duration.{Duration, DurationInt}
import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient
import com.whisk.docker.impl.spotify.SpotifyDockerCommandExecutor
import com.whisk.docker.scalatest._
import org.scalacheck._

class SpotifyDockerFactory(client: DockerClient) extends DockerFactory {

  override def createExecutor(): DockerCommandExecutor = {
    new SpotifyDockerCommandExecutor(client.getHost, client)
  }
}

trait DockerConsulService extends DockerKit {

  override implicit val dockerFactory: DockerFactory =
    new SpotifyDockerFactory(DefaultDockerClient.fromEnv().build())

  val consulContainer = DockerContainer("progrium/consul")
    .withPorts(8500 -> Some(18512))
    .withCommand("-server", "-bootstrap")
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
  val baseUrl = Uri.uri("http://127.0.0.1:18512")
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
