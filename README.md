# Helm

![Logo](docs/src/img/logo.png)

[![Build Status](https://travis-ci.org/Verizon/helm.svg?branch=master)](https://travis-ci.org/Verizon/helm)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.verizon.helm/core_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.verizon.helm/core_2.11)
[![codecov](https://codecov.io/gh/Verizon/helm/branch/master/graph/badge.svg)](https://codecov.io/gh/Verizon/helm)

A native [Scala](http://scala-lang.org) client for interacting with [Consul](https://www.consul.io/). There is currently one supported client, which uses [http4s](http://http4s.org) to make HTTP calls to Consul. Alternative implementations could be added with relative ease by providing an additional free interpreter for the `ConsulOp` algebra.

## Getting Started

Add the following to your `build.sbt`:

    libraryDependencies += "io.verizon.helm" %% "http4s" % "1.3.+"

The *Helm* binaries are located on maven central, so no additional resolvers are needed.

### Algebra

Consul operations are specified by the `ConsulOp` algebra.  Two
examples are `get` and `set`:

```
import helm._

val s: ConsulOpF[Unit] = : ConsulOp.set("key", "value")

val g: ConsulOpF[Option[String]] = : ConsulOp.get("key")
```

These are however just descriptions of what operations the program might perform in the future, just creating these operations does not
actually execute the operations. In order to perform the gets and sets, we need to use the [http4s](http://http4s.org) interpreter.

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

// actually execute the calls
s.run
g.run
```

Typically, the *Helm* algebra would be a part of a `Coproduct` with other algebras in a larger program, so running the `Task` immediately after `helm.run` is not typical.

## Contributing

Contributions are welcome; particularly to expand the algebra with additional operations that are supported by Consul but not yet supported by *Helm*.

