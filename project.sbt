
organization in Global := "io.verizon.helm"

crossScalaVersions in Global := Seq("2.11.8", "2.10.6")

scalaVersion in Global := crossScalaVersions.value.head

lazy val helm = project.in(file(".")).aggregate(core, http4s)

lazy val core = project

lazy val http4s = project dependsOn core

enablePlugins(DisablePublishingPlugin)
