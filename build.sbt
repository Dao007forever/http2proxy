scalaVersion := "2.11.8"

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)

libraryDependencies ++= Seq(
  "io.grpc" % "grpc-netty" % com.trueaccord.scalapb.compiler.Version.grpcJavaVersion,
  "com.trueaccord.scalapb" %% "scalapb-runtime-grpc" % com.trueaccord.scalapb.compiler.Version.scalapbVersion,
  "org.mashupbots.socko" % "socko-webserver_2.11" % "0.6.0",
  "io.grpc" % "grpc-all" % "1.6.1",
  "com.typesafe.scala-logging" % "scala-logging-slf4j_2.11" % "2.1.2"
)
