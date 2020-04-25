import mill._
import mill.scalalib._

object Dependencies {
  lazy val circe = {
    val version = "0.13.0"
    Agg(
      ivy"io.circe::circe-core:$version",
      ivy"io.circe::circe-generic:$version",
      ivy"io.circe::circe-parser:$version"
    )
  }

  lazy val http4s = {
    val version = "0.21.2"
    Agg(
      ivy"org.http4s::http4s-dsl:$version",
      ivy"org.http4s::http4s-circe:$version",
      ivy"org.http4s::http4s-blaze-server:$version",
      ivy"org.http4s::http4s-blaze-client:$version"
    )
  }

  val akkaHttpVersion = "10.1.11"
  lazy val akkaHttp = {
    Agg(
      ivy"com.typesafe.akka::akka-http:$akkaHttpVersion",
      ivy"com.typesafe.akka::akka-stream:2.6.4"
    )
  }

  lazy val circeYaml = Agg(ivy"io.circe::circe-yaml:0.12.0")

  lazy val bouncycastle = Agg(ivy"org.bouncycastle:bcpkix-jdk15on:1.65")

  lazy val collectionCompat = Agg(ivy"org.scala-lang.modules::scala-collection-compat:2.1.4")

  lazy val logging = Agg(
    ivy"io.chrisdavenport::log4cats-slf4j:1.0.1",
    ivy"ch.qos.logback:logback-classic:1.2.3"
  )

  lazy val tests = Agg(
    ivy"org.scalatest::scalatest:3.1.1",
    ivy"com.github.julien-truffaut::monocle-core:2.0.4",
    ivy"com.typesafe.akka::akka-http-testkit:$akkaHttpVersion"
  )

}
