import $ivy.`com.goyeau::mill-git::0.2.7`
import $ivy.`com.goyeau::mill-scalafix::0.5.0`
import $ivy.`org.typelevel::scalac-options:0.1.7`

import $file.project.Dependencies
import Dependencies.{bouncycastle, circe, circeYaml, collectionCompat, http4s, java8compat, logback, logging, tests}
import $file.project.{SwaggerModelGenerator => SwaggerModelGeneratorFile}
import SwaggerModelGeneratorFile.SwaggerModelGenerator
import com.goyeau.mill.git.{GitVersionModule, GitVersionedPublishModule}
import com.goyeau.mill.scalafix.StyleModule
import mill._
import mill.scalalib.TestModule.Munit
import mill.scalalib._
import mill.scalalib.api.ZincWorkerUtil.isScala3
import mill.scalalib.publish.{Developer, License, PomSettings, VersionControl}
import org.typelevel.scalacoptions.ScalacOptions.{fatalWarningOptions, maxInlines, release, source3}
import org.typelevel.scalacoptions.{ScalaVersion, ScalacOptions}

object `kubernetes-client` extends Cross[KubernetesClientModule]("3.3.4", "2.13.16", "2.12.20")
trait KubernetesClientModule
    extends CrossScalaModule
    with StyleModule
    with GitVersionedPublishModule
    with SonatypeCentralPublishModule
    with SwaggerModelGenerator {
  def kubernetesSwagger      = downloadedKubernetesSwagger
  lazy val jvmVersion        = "11"
  override def javacOptions  = super.javacOptions() ++ Seq("-source", jvmVersion, "-target", jvmVersion)
  override def scalacOptions = super.scalacOptions() ++ ScalacOptions.tokensForVersion(
    ScalaVersion.unsafeFromString(scalaVersion()),
    ScalacOptions.default + release(jvmVersion) + source3 + maxInlines(50) // ++ fatalWarningOptions
  )

  override def ivyDeps =
    super.ivyDeps() ++ http4s ++ circe ++ circeYaml ++ bouncycastle ++ collectionCompat ++ logging ++ java8compat
  override def scalacPluginIvyDeps = super.scalacPluginIvyDeps() ++
    (if (isScala3(scalaVersion())) Agg.empty else Agg(ivy"org.typelevel:::kind-projector:0.13.3"))

  object test extends ScalaTests with Munit {
    override def forkArgs = super.forkArgs() :+ "-Djdk.tls.client.protocols=TLSv1.2"
    override def ivyDeps  = super.ivyDeps() ++ tests ++ logback
  }

  override def publishVersion = GitVersionModule.version(withSnapshotSuffix = true)
  def pomSettings             = PomSettings(
    description = "A Kubernetes client for Scala",
    organization = "com.goyeau",
    url = "https://github.com/joan38/kubernetes-client",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("joan38", "kubernetes-client"),
    developers = Seq(Developer("joan38", "Joan Goyeau", "https://github.com/joan38"))
  )
}

def kubernetesVersion: T[String] = T("1.31.1")

def downloadedKubernetesSwagger: T[String] = T {
  requests
    .get(
      s"https://raw.githubusercontent.com/kubernetes/kubernetes/refs/tags/v${kubernetesVersion()}/api/openapi-spec/swagger.json"
    )
    .text()
}
