import consul.Dependencies._
import verizon.build._

common.settings

metadata.settings

libraryDependencies ++= Seq(
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.2",
  "verizon.inf.delorean" %% "core" % "1.1.+",
  journal.core
)

(initialCommands in console) := """
import consul._
import dispatch._
import scalaz.concurrent.Strategy
import _root_.dispatch._, _root_.dispatch.Defaults._

val h = host("127.0.0.1", 8500)
val http = Http.configure(_.setAllowPoolingConnection(true).setConnectionTimeoutInMs(20000))
import scala.concurrent.ExecutionContext.global

val c = new DispatchConsulClient(h, http, implicitly,Strategy.DefaultStrategy)
"""
