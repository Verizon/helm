# Helm

[![Build Status](https://travis.oncue.verizon.net/iptv/helm.svg?token=Lp2ZVD96vfT8T599xRfV&branch=master)](https://travis.oncue.verizon.net/iptv/helm)

A client for getting / setting values from a consul KV store.

There is currently one supported client, which uses [http4s](http://http4s.org)
to make HTTP calls to consul.

## Getting Started

Add the following to your build.sbt:

    libraryDependencies += "verizon.inf.helm" %% "http4s" % "1.2.+"

### ConsulOp

Consul operations are specified by the `ConsulOp` algebra.  Two
examples are `get` and `set`:

```
import helm._

val s: ConsulOpF[Unit] = : ConsulOp.set("key", "value")

val g: ConsulOpF[Option[String]] = : ConsulOp.get("key")
```

These are however just descriptions of what operations we might
perform in the future, just creating these operations does not
actually perform any task. In order to perform the gets and sets, we
need to use the http4s interpreter.

### The http4s interpreter

First we create an interpreter, which requires an http4s client and
a base url for consul:

```
import helm.http4s._
import org.http4s.Uri.uri
import org.http4s.client.blaze.PooledHttp1Client

val client = PooledHttp1Client()
val baseUrl = uri("http://127.0.0.1:8500")

val interpreter = new Http4sConsulClient(baseUrl, client)
```

Now we can apply commands to our http4s client to get back Tasks
which actually interact with consul.

```
import scalaz.concurrent.Task

val s: Task[Unit] = helm.run(ConsulOp.set("testkey", "testvalue"))(interpreter)

val g: Task[String] = helm.run(ConsulOp.get("testkey"))(interpreter)

s.run
g.run
```
