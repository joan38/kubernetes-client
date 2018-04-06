name := "Kubernetes Client"
organization := "com.goyeau"
scalaVersion := "2.12.5"
dynverSonatypeSnapshots := true
scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-Xlint:unsound-match",
  "-Ywarn-inaccessible",
  "-Ywarn-infer-any",
  "-Ywarn-unused:imports",
  "-Ywarn-unused:locals",
  "-Ywarn-unused:patvars",
  "-Ywarn-unused:privates",
  "-Ypartial-unification",
  "-Ywarn-dead-code"
)
enablePlugins(SwaggerModelGenerator)
libraryDependencies ++= akkaHttp ++ circe ++ logging ++ circeYaml ++ bouncycastle

licenses += "APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0")
homepage := Option(url("https://github.com/joan38/kubernetes-client"))
scmInfo := Option(
  ScmInfo(
    url("https://github.com/joan38/kubernetes-client"),
    "https://github.com/joan38/kubernetes-client.git"
  )
)
developers += Developer(id = "joan38", name = "Joan Goyeau", email = "joan@goyeau.com", url = url("http://goyeau.com")
)
publishTo := Option(
  if (isSnapshot.value) Opts.resolver.sonatypeSnapshots
  else Opts.resolver.sonatypeStaging
)

lazy val circe = {
  val circeVersion = "0.9.3"
  Seq(
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion
  )
}

lazy val akkaHttp = {
  val akkaHttpVersion = "10.1.1"
  Seq(
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-stream" % "2.5.11",
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test
  )
}

lazy val circeYaml = Seq("io.circe" %% "circe-yaml" % "0.7.0")

lazy val bouncycastle = Seq("org.bouncycastle" % "bcpkix-jdk15on" % "1.59")

lazy val logging = Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.8.0",
  "ch.qos.logback" % "logback-classic" % "1.2.3"
)
