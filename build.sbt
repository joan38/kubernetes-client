name := "Kubernetes Client"
organization := "com.goyeau"
scalaVersion := "2.13.1"

libraryDependencies += compilerPlugin(scalafixSemanticdb)
addCommandAlias("fix", "; compile:scalafix; test:scalafix")
addCommandAlias("fixCheck", "; compile:scalafix --check; test:scalafix --check")
addCommandAlias("fmt", "; compile:scalafmt; test:scalafmt; scalafmtSbt")
addCommandAlias("fmtCheck", "; compile:scalafmtCheck; test:scalafmtCheck; scalafmtSbtCheck")
scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-language:higherKinds",
  "-Xlint:inaccessible",
  "-Xlint:infer-any",
  "-Ywarn-unused:imports",
  "-Ywarn-unused:locals",
  "-Ywarn-unused:patvars",
  "-Ywarn-unused:privates",
  "-Ywarn-dead-code"
)
enablePlugins(SwaggerModelGenerator)
libraryDependencies ++= http4s ++ akkaHttp ++ circe ++ logging ++ circeYaml ++ bouncycastle ++ tests

licenses += "APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0")
homepage := Option(url("https://github.com/joan38/kubernetes-client"))
scmInfo := Option(
  ScmInfo(
    url("https://github.com/joan38/kubernetes-client"),
    "https://github.com/joan38/kubernetes-client.git"
  )
)
developers += Developer(id = "joan38", name = "Joan Goyeau", email = "joan@goyeau.com", url = url("http://goyeau.com"))
Global / releaseEarlyWith := SonatypePublisher
Global / releaseEarlyEnableLocalReleases := true

lazy val circe = {
  val circeVersion = "0.13.0"
  Seq(
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion
  )
}

lazy val http4s = {
  val version = "0.21.2"
  Seq(
    "org.http4s" %% "http4s-dsl" % version,
    "org.http4s" %% "http4s-circe" % version,
    "org.http4s" %% "http4s-blaze-server" % version,
    "org.http4s" %% "http4s-blaze-client" % version
  )
}

lazy val akkaHttp = {
  val akkaHttpVersion = "10.1.11"
  Seq(
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-stream" % "2.6.4",
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test
  )
}

lazy val circeYaml = Seq("io.circe" %% "circe-yaml" % "0.12.0")

lazy val bouncycastle = Seq("org.bouncycastle" % "bcpkix-jdk15on" % "1.60")

lazy val logging = Seq(
  "io.chrisdavenport" %% "log4cats-slf4j" % "1.0.1",
  "ch.qos.logback" % "logback-classic" % "1.2.3"
)

lazy val tests = Seq(
  "org.scalactic" %% "scalactic" % "3.1.1",
  "org.scalatest" %% "scalatest" % "3.1.1" % Test,
  "com.github.julien-truffaut" %% "monocle-core" % "2.0.4" % Test
)
