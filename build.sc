import $ivy.`com.goyeau::mill-git::0.2.4`
import $ivy.`com.goyeau::mill-scalafix::0.2.11`
import $ivy.`io.github.davidgregory084::mill-tpolecat::0.3.5`
import $file.project.Dependencies
import Dependencies.Dependencies._
import $file.project.{SwaggerModelGenerator => SwaggerModelGeneratorFile}
import SwaggerModelGeneratorFile.SwaggerModelGenerator
import com.goyeau.mill.git.{GitVersionModule, GitVersionedPublishModule}
import com.goyeau.mill.scalafix.StyleModule
import io.github.davidgregory084.TpolecatModule
import mill._
import mill.scalalib.TestModule.Munit
import mill.scalalib._
import mill.scalalib.api.ZincWorkerUtil.isScala3
import mill.scalalib.publish.{Developer, License, PomSettings, VersionControl}

object `kubernetes-client` extends Cross[KubernetesClientModule]("3.2.1", "2.13.10", "2.12.17")
class KubernetesClientModule(val crossScalaVersion: String)
    extends CrossScalaModule
    with TpolecatModule
    with StyleModule
    with GitVersionedPublishModule
    with SwaggerModelGenerator {

  override def scalacOptions =
    super
      .scalacOptions()
      .filterNot(Set("-Xfatal-warnings")) ++
      (if (isScala3(scalaVersion())) Seq("-language:Scala2", "-Xmax-inlines", "50") else Seq.empty)

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
