
libraryDependencies ++= Seq(
  "io.argonaut"                %% "argonaut"          % "6.2",
  "org.typelevel"              %% "cats-free"         % "1.0.0-RC1",
  "org.typelevel"              %% "cats-effect"       % "0.5"
)

addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.5" cross CrossVersion.binary)
