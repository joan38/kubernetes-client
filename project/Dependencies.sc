import mill._
import mill.scalalib._

object Dependencies {
  lazy val circe = {
    val version = "0.14.6"
    Agg(
      ivy"io.circe::circe-core::$version",
      ivy"io.circe::circe-generic::$version",
      ivy"io.circe::circe-parser::$version"
    )
  }

  object http4s {
    // private val version          = "0.23.23"
    private val version          = "0.23.23-101-eb5dd80-SNAPSHOT"
    private val jdkClientVersion = "0.9.1"
    val core = Agg(
      ivy"org.http4s::http4s-dsl::$version",
      ivy"org.http4s::http4s-circe::$version",
      ivy"org.http4s::http4s-client::$version",
    )
    val jdkClient = Agg(ivy"org.http4s::http4s-jdk-http-client::${jdkClientVersion}")
    val emberClient = Agg(ivy"org.http4s::http4s-ember-client::${version}")
  }

  lazy val circeYaml = Agg(ivy"com.armanbilge::circe-scala-yaml::0.0.4")

  lazy val bouncycastle = Agg(ivy"org.bouncycastle:bcpkix-jdk18on:1.77")

  lazy val collectionCompat = Agg(ivy"org.scala-lang.modules::scala-collection-compat:2.11.0")

  object log4cats {
    private val version  = "2.6.0"

    val core = Agg(ivy"org.typelevel::log4cats-core:2.6.0")
    
    val logback = Agg(
      ivy"org.typelevel::log4cats-slf4j:2.6.0",
      ivy"ch.qos.logback:logback-classic:1.4.11"
      )

    val jsConsole = Agg(ivy"org.typelevel::log4cats-js-console::2.6.0")
  }
  
  lazy val java8compat = Agg(ivy"org.scala-lang.modules::scala-java8-compat::1.0.2")

  lazy val scalajsJavaTime = Agg(ivy"io.github.cquiroz::scala-java-time::2.5.0")

  lazy val tests = Agg(
    ivy"org.scalameta::munit::1.0.0-M10",
    ivy"org.typelevel::munit-cats-effect::2.0.0-M4"
  )
}
