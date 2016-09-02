
enablePlugins(GithubReleasePlugin)

teamName in Global := Some("inf")

projectName in Global := Some("helm")

scalaVersion in Global := "2.11.7"

lazy val helm = project.in(file(".")).aggregate(core, http4s)

lazy val core = project

lazy val http4s = project dependsOn core
