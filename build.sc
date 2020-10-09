import $ivy.`com.goyeau::mill-git:0.1.1`
import $ivy.`com.goyeau::mill-scalafix:0.1.4`
import $ivy.`com.lihaoyi::mill-contrib-bsp:$MILL_VERSION`
import $ivy.`io.github.davidgregory084::mill-tpolecat:0.1.4`
import $file.project.Dependencies, Dependencies.Dependencies._
import $file.project.{SwaggerModelGenerator => SwaggerModelGeneratorFile}
import SwaggerModelGeneratorFile.SwaggerModelGenerator
import com.goyeau.mill.git.GitVersionedPublishModule
import com.goyeau.mill.scalafix.StyleModule
import io.github.davidgregory084.TpolecatModule
import mill._
import mill.scalalib._
import mill.scalalib.publish.{Developer, License, PomSettings, VersionControl}

object `kubernetes-client` extends Cross[KubernetesClientModule]("2.13.2", "2.12.11")
class KubernetesClientModule(val crossScalaVersion: String)
    extends CrossScalaModule
    with TpolecatModule
    with StyleModule
    with GitVersionedPublishModule
    with SwaggerModelGenerator {
  override def scalacOptions =
    super.scalacOptions().filter(_ != "-Wunused:imports") ++
      (if (scalaVersion().startsWith("2.12")) Seq("-Ypartial-unification") else Seq.empty)
  override def ivyDeps =
    super.ivyDeps() ++ http4s ++ circe ++ circeYaml ++ bouncycastle ++ collectionCompat ++ logging

  object test extends Tests {
    def testFrameworks    = Seq("munit.Framework")
    override def forkArgs = super.forkArgs() :+ "-Djdk.tls.client.protocols=TLSv1.2"
    override def ivyDeps  = super.ivyDeps() ++ Agg(ivy"org.scalameta::munit:0.7.14")
  }

  override def artifactName = "kubernetes-client"
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
