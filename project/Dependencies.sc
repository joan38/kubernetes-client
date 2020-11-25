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
    val jdkClientVersion = "0.3.0"
    Agg(
      ivy"org.http4s::http4s-dsl:$version",
      ivy"org.http4s::http4s-circe:$version",
      ivy"org.http4s::http4s-blaze-client:$version",
      ivy"org.http4s::http4s-jdk-http-client:$jdkClientVersion"
    )
  }

  lazy val circeYaml = Agg(ivy"io.circe::circe-yaml:0.13.1")

  lazy val bouncycastle = Agg(ivy"org.bouncycastle:bcpkix-jdk15on:1.66")

  lazy val collectionCompat = Agg(ivy"org.scala-lang.modules::scala-collection-compat:2.3.1")

  lazy val logging = Agg(ivy"io.chrisdavenport::log4cats-slf4j:1.1.1")
}
