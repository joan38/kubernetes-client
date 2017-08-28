name := "kubernetes-client"
organization := "com.goyeau"
scalaVersion := "2.12.3"
scalacOptions += "-deprecation"

enablePlugins(SwaggerModelGenerator)

val circe = {
  val circeVersion = "0.8.0"
  Seq(
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion,
    "io.circe" %% "circe-shapes" % circeVersion
  )
}

val akkaHttp = {
  val akkaHttpVersion = "10.0.9"
  Seq(
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test
  )
}

val moultingyaml = Seq("net.jcazevedo" %% "moultingyaml" % "0.4.0")

val bouncycastle = Seq("org.bouncycastle" % "bcpkix-jdk15on" % "1.58")

val logging = Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.7.1",
  "ch.qos.logback" % "logback-classic" % "1.2.3"
)

libraryDependencies ++= akkaHttp ++ circe ++ logging ++ moultingyaml ++ bouncycastle
