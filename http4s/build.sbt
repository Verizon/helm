import consul.Dependencies._
import verizon.build._

common.settings

metadata.settings

val http4sOrg = "verizon.thirdparty.http4s"
val http4sVersion = "0.12.10"

libraryDependencies ++= Seq(
  http4sOrg %% "http4s-client" % http4sVersion,
  http4sOrg %% "http4s-argonaut" % http4sVersion,
  journal.core
)
