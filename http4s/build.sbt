import Dependencies._

val http4sOrg = "org.http4s"
val http4sVersion = "0.15.3"
val dockeritVersion = "0.9.0"

scalaTestVersion  := "3.0.1"
scalaCheckVersion := "1.13.4"

libraryDependencies ++= Seq(
  http4sOrg        %% "http4s-blaze-client"             % http4sVersion,
  http4sOrg        %% "http4s-argonaut61"               % http4sVersion,
  "com.whisk"      %% "docker-testkit-scalatest"        % dockeritVersion % "test",
  "com.whisk"      %% "docker-testkit-impl-docker-java" % dockeritVersion % "test",
  journal.core
)

(initialCommands in console) := """
import helm._
import http4s._

import scala.concurrent.duration.{DurationInt,Duration}

import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.client.blaze.PooledHttp1Client
import org.http4s.headers.{AgentProduct, `User-Agent`}
import org.http4s.util.threads
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.{Executors, ExecutorService}
import javax.net.ssl.SSLContext

val maxTotalConnections: Int = 10
val idleTimeout: Duration = 60.seconds
val requestTimeout: Duration = 3.seconds
val bufferSize: Int = 8*1024
val userAgent = Some(`User-Agent`(AgentProduct("http4s-blaze", Some(org.http4s.BuildInfo.version))))
def clientEC() = {
  val maxThreads = math.max(4, (Runtime.getRuntime.availableProcessors * 1.5).ceil.toInt)
  val threadFactory = threads.threadFactory(name = (i => s"http4s-blaze-client-$i"), daemon = true)
  Executors.newFixedThreadPool(maxThreads, threadFactory)
}

val http = PooledHttp1Client(maxTotalConnections, idleTimeout, requestTimeout, userAgent, bufferSize, clientEC, None, true, None)

val c = new Http4sConsulClient(Uri.uri("http://localhost:8500"), http)

"""
