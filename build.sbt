name := "Kubernetes Client"
organization := "com.goyeau"
scalaVersion := "2.12.3"
version := {
  val ver = version.value
  if (!ver.contains("+")) ver
  else ver + "-SNAPSHOT"
}
scalacOptions += "-deprecation"
enablePlugins(SwaggerModelGenerator)
libraryDependencies ++= akkaHttp ++ circe ++ logging ++ moultingyaml ++ bouncycastle

licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
homepage := Option(url("https://github.com/joan38/kubernetes-client"))
scmInfo := Option(
  ScmInfo(
    url("https://github.com/joan38/kubernetes-client"),
    "https://github.com/joan38/kubernetes-client.git"
  )
)
developers := List(
  Developer(id = "joan38", name = "Joan Goyeau", email = "joan@goyeau.com", url = url("http://goyeau.com"))
)
publishTo := Option(
  if (isSnapshot.value) Opts.resolver.sonatypeSnapshots
  else Opts.resolver.sonatypeStaging
)
publishMavenStyle := true

lazy val circe = {
  val circeVersion = "0.8.0"
  Seq(
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion
  )
}

lazy val akkaHttp = {
  val akkaHttpVersion = "10.0.9"
  Seq(
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test
  )
}

lazy val moultingyaml = Seq("net.jcazevedo" %% "moultingyaml" % "0.4.0")

lazy val bouncycastle = Seq("org.bouncycastle" % "bcpkix-jdk15on" % "1.58")

lazy val logging = Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.7.1",
  "ch.qos.logback" % "logback-classic" % "1.2.3"
)
