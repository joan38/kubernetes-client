import $ivy.`com.goyeau::mill-git:0.2.0`
import $ivy.`com.goyeau::mill-scalafix:0.2.4`
import $ivy.`io.github.davidgregory084::mill-tpolecat:0.2.0`
import $file.project.Dependencies, Dependencies.Dependencies._
import $file.project.{SwaggerModelGenerator => SwaggerModelGeneratorFile}
import SwaggerModelGeneratorFile.SwaggerModelGenerator
import com.goyeau.mill.git.{GitVersionModule, GitVersionedPublishModule}
import com.goyeau.mill.scalafix.StyleModule
import io.github.davidgregory084.TpolecatModule
import mill._
import mill.scalalib._
import mill.scalalib.publish.{Developer, License, PomSettings, VersionControl}

object `kubernetes-client` extends Cross[KubernetesClientModule]("2.13.5", "2.12.12")
class KubernetesClientModule(val crossScalaVersion: String)
    extends CrossScalaModule
    with TpolecatModule
    with StyleModule
    with GitVersionedPublishModule
    with SwaggerModelGenerator {

  override def scalacOptions = super.scalacOptions()
    .filter(_ != "-Wunused:imports")
    .filter(_ != "-Xfatal-warnings")

  override def ivyDeps =
    super.ivyDeps() ++ http4s ++ circe ++ circeYaml ++ bouncycastle ++ collectionCompat ++ logging
  override def scalacPluginIvyDeps = super.scalacPluginIvyDeps() ++ Agg(ivy"org.typelevel:::kind-projector:0.11.3")

  object test extends Tests {
    def testFrameworks    = Seq("munit.Framework")
    override def forkArgs = super.forkArgs() :+ "-Djdk.tls.client.protocols=TLSv1.2"
    override def ivyDeps  = super.ivyDeps() ++ Agg(ivy"org.scalameta::munit:0.7.26")
  }

  override def publishVersion = GitVersionModule.version(withSnapshotSuffix = true)
  def pomSettings =
    PomSettings(
      description = "A Kubernetes client for Scala",
      organization = "com.goyeau",
      url = "https://github.com/joan38/kubernetes-client",
      licenses = Seq(License.`Apache-2.0`),
      versionControl = VersionControl.github("joan38", "kubernetes-client"),
      developers = Seq(Developer("joan38", "Joan Goyeau", "https://github.com/joan38"))
    )
}
