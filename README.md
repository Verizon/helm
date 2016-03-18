A client for getting / setting values from a consul KV store.

There are two supported clients, which differ only in which http
client library they use. One uses [http4s](http://http4s.org) the
other uses
[dispatch](http://dispatch.databinder.net/Dispatch.html). We recommend
the http4s client, but we also provide the dispatch client as an
alternative for cases when there are dependency conflicts with the
http4s library.

=== Getting Started - sbt

add one of the following to your build.sbt:

    libraryDependencies += "verizon.inf.consul" %% "dispatch" % "0.0.1"

or

    libraryDependencies += "verizon.inf.consul" %% "http4s" % "0.0.1"

=== Verbs: Getting and Setting

There are currently only two supported operations, get and set:

    import consul._

	val s: ConsulOpF[Unit] = : ConsulOp.set("key", "value")

	val g: ConsulOpF[String] = : ConsulOp.get("key")

These are however just descriptions of what operations we might
perform in the future, just creating these operations does not
actually perform any task. In order to perform the gets and sets, we
need to use either the http4s or dispatch interpreter.


=== The dispatch interpreter

First we create an interpreter, which requires a dispatch client, a
base url for consul, and an ExecutionContext.

    import consul.dispatch
	import _root_.dispatch._, _root_.dispatch.Defaults._
	
	import scala.concurrent.ExecutionContext.global // don't actually use this in real code

	val baseUrl = host("127.0.0.1", 8500) / "v1" / "kv"
	val client = Http.configure(_.setAllowPoolingConnection(true).setConnectionTimeoutInMs(20000))

	val c = new DispatchConsulClient(baseUrl, client, implicitly)


Now we can apply commands to our dispatch client to get back Tasks
which actually interact with consul.

	import scalaz.concurrent.Task
	
	val s: Task[Unit] = consul.run(ConsulOp.set("testkey", "testvalue"))(c)
    val g: Task[String] = consul.run(ConsulOp.get("testkey"))(c)

    s.run
    g.run


