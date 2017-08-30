
libraryDependencies ++= Seq(
  "io.argonaut"                %% "argonaut-cats"     % "6.2",
  "org.typelevel"              %% "cats-free"         % "0.9.0",
  "org.typelevel"              %% "cats-effect"       % "0.3"
)

addCompilerPlugin("org.spire-math" % "kind-projector" % "0.9.4" cross CrossVersion.binary)
