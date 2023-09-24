ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.12"

enablePlugins(JavaAppPackaging)

lazy val akkaVersion = "2.8.0"
lazy val akkaHttpVersion = "10.5.2"

lazy val root = (project in file("."))
  .settings(
    name := "sse_service",

    Compile / compile / mainClass := Some("Service"),
    Compile / run / mainClass := Some("Service"),

    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "ch.qos.logback" % "logback-classic" % "1.4.7",

      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
      "org.scalatest" %% "scalatest" % "3.2.15" % Test
    )
  )

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

