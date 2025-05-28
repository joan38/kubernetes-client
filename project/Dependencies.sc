import mill._
import mill.scalalib._

lazy val circe = {
  val version = "0.14.10"
  Agg(
    ivy"io.circe::circe-core:$version",
    ivy"io.circe::circe-generic:$version",
    ivy"io.circe::circe-parser:$version"
  )
}

lazy val http4s = {
  val version          = "0.23.30"
  val jdkClientVersion = "0.5.0"
  Agg(
    ivy"org.http4s::http4s-dsl:$version",
    ivy"org.http4s::http4s-circe:$version",
    ivy"org.http4s::http4s-jdk-http-client:$jdkClientVersion"
  )
}

lazy val circeYaml = Agg(ivy"io.circe::circe-yaml:0.15.2")

lazy val bouncycastle = Agg(ivy"org.bouncycastle:bcpkix-jdk18on:1.80")

lazy val collectionCompat = Agg(ivy"org.scala-lang.modules::scala-collection-compat:2.13.0")

lazy val logging = Agg(ivy"org.typelevel::log4cats-slf4j:2.7.1")

lazy val logback = Agg(ivy"ch.qos.logback:logback-classic:1.5.18")

lazy val java8compat = Agg(ivy"org.scala-lang.modules::scala-java8-compat:1.0.2")

lazy val tests = Agg(ivy"org.scalameta::munit:1.1.0")
