import mill._
import mill.scalalib._

object Dependencies {
  lazy val circe = {
    val version = "0.14.1"
    Agg(
      ivy"io.circe::circe-core:$version",
      ivy"io.circe::circe-generic:$version",
      ivy"io.circe::circe-parser:$version"
    )
  }

  lazy val http4s = {
    val version = "0.22.11"
    val jdkClientVersion = "0.4.0"
    Agg(
      ivy"org.http4s::http4s-dsl:$version",
      ivy"org.http4s::http4s-circe:$version",
      ivy"org.http4s::http4s-jdk-http-client:$jdkClientVersion"
    )
  }

  lazy val circeYaml = Agg(ivy"io.circe::circe-yaml:0.14.0")

  lazy val bouncycastle = Agg(ivy"org.bouncycastle:bcpkix-jdk15on:1.68")

  lazy val collectionCompat = Agg(ivy"org.scala-lang.modules::scala-collection-compat:2.4.4")

  lazy val logging = Agg(ivy"org.typelevel::log4cats-slf4j:2.1.1")

  lazy val logback = Agg(ivy"ch.qos.logback:logback-classic:1.2.3")
}
