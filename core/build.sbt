import verizon.build._

common.settings

metadata.settings

libraryDependencies ++= Seq(
  "io.argonaut"                %% "argonaut"          % "6.1",
  "org.scalaz"                 %% "scalaz-core"       % "7.1.7",
  "org.scalaz"                 %% "scalaz-concurrent" % "7.1.7",
  "verizon.inf.tortuga"        %% "scalatest"         % "1.0.+"   % "test"
)
