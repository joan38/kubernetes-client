import $ivy.`com.goyeau::mill-git::0.2.5`
import $ivy.`com.goyeau::mill-scalafix::0.3.1`
import $ivy.`org.typelevel::scalac-options:0.1.4`

import $file.project.Dependencies
import Dependencies.Dependencies._
import $file.project.SwaggerModelGenerator
import SwaggerModelGenerator.SwaggerModelGenerator
import com.goyeau.mill.git.{GitVersionModule, GitVersionedPublishModule}
import com.goyeau.mill.scalafix.StyleModule
import mill._
import mill.scalalib.TestModule.Munit
import mill.scalalib._
import mill.scalalib.api.ZincWorkerUtil.isScala3
import mill.scalalib.publish.{Developer, License, PomSettings, VersionControl}
import org.typelevel.scalacoptions.ScalacOptions.{advancedOption, fatalWarningOptions, release, source3}
import org.typelevel.scalacoptions.{ScalaVersion, ScalacOptions}

object `kubernetes-client` extends Cross[KubernetesClientModule]("3.3.1", "2.13.10", "2.12.17")
class KubernetesClientModule(val crossScalaVersion: String)
    extends CrossScalaModule
    with StyleModule
    with GitVersionedPublishModule
    with SwaggerModelGenerator {
  lazy val jvmVersion       = "11"
  override def javacOptions = super.javacOptions() ++ Seq("-source", jvmVersion, "-target", jvmVersion)
  override def scalacOptions = super.scalacOptions() ++ ScalacOptions.tokensForVersion(
    scalaVersion() match {
      case "3.3.1"   => ScalaVersion.V3_3_1
      case "2.13.10" => ScalaVersion.V2_13_9
      case "2.12.17" => ScalaVersion.V2_12_13
    },
    ScalacOptions.default + release(jvmVersion) + source3 +
      advancedOption("max-inlines", List("50"), _.isAtLeast(ScalaVersion.V3_0_0)) // ++ fatalWarningOptions
  )

  override def ivyDeps =
    super.ivyDeps() ++ http4s ++ circe ++ circeYaml ++ bouncycastle ++ collectionCompat ++ logging
  override def scalacPluginIvyDeps = super.scalacPluginIvyDeps() ++
    (if (isScala3(scalaVersion())) Agg.empty else Agg(ivy"org.typelevel:::kind-projector:0.13.2"))

  object test extends Tests with Munit {
    override def forkArgs = super.forkArgs() :+ "-Djdk.tls.client.protocols=TLSv1.2"
    override def ivyDeps  = super.ivyDeps() ++ tests ++ logback
  }

  override def publishVersion = GitVersionModule.version(withSnapshotSuffix = true)
  def pomSettings = PomSettings(
    description = "A Kubernetes client for Scala",
    organization = "com.goyeau",
    url = "https://github.com/joan38/kubernetes-client",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("joan38", "kubernetes-client"),
    developers = Seq(Developer("joan38", "Joan Goyeau", "https://github.com/joan38"))
  )
}
