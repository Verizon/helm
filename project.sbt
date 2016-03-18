import verizon.build._

common.settings

ghrelease.settings

teamName in Global := Some("inf")

projectName in Global := Some("consul")

scalaVersion in Global := "2.11.7"

lazy val consul = project.in(file(".")).aggregate(core, http4s, dispatch)

lazy val core = project

lazy val http4s = project dependsOn core

lazy val dispatch = project dependsOn core

