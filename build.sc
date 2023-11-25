import $ivy.`com.goyeau::mill-git::0.2.5`
import $ivy.`com.goyeau::mill-scalafix::0.3.1`
import $ivy.`org.typelevel::scalac-options:0.1.4`

import $file.project.Dependencies
import Dependencies.Dependencies._
import $file.project.{SwaggerModelGenerator => SwaggerModelGeneratorFile}
import SwaggerModelGeneratorFile.SwaggerModelGenerator
import com.goyeau.mill.git.{GitVersionModule, GitVersionedPublishModule}
import com.goyeau.mill.scalafix.StyleModule
import mill._
import mill.scalalib._
import mill.scalajslib._
import mill.scalanativelib._
import mill.scalajslib.api.ModuleKind
import mill.scalalib.api.ZincWorkerUtil.isScala3
import mill.scalalib.publish.{Developer, License, PomSettings, VersionControl}
import org.typelevel.scalacoptions.ScalacOptions.{advancedOption, fatalWarningOptions, release, source3}
import org.typelevel.scalacoptions.{ScalaVersion, ScalacOptions}
import coursier.maven.MavenRepository

object `kubernetes-client` extends Cross[KubernetesClientModule]("3.3.1", "2.13.10" /* "2.12.17" */)
trait KubernetesClientModule extends Cross.Module[String] {
  trait Shared
      extends CrossScalaModule
      with CrossValue
      with PlatformScalaModule
      with StyleModule
      with GitVersionedPublishModule
      with SwaggerModelGenerator {

    lazy val jvmVersion       = "11"

    override def repositoriesTask = T.task {
      super.repositoriesTask() ++ Seq(
        coursier.Repositories.sonatype("snapshots"), 
        coursier.Repositories.sonatypeS01("snapshots")
      )
    }

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
      super.ivyDeps() ++ http4s.core ++ circe ++ circeYaml ++ bouncycastle ++ collectionCompat ++ log4cats.core // ++ java8compat
      
    override def scalacPluginIvyDeps = super.scalacPluginIvyDeps() ++
      (if (isScala3(scalaVersion())) Agg.empty else Agg(ivy"org.typelevel:::kind-projector:0.13.2"))

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

  trait SharedTestModule extends ScalaModule with TestModule.Munit {
    override def forkArgs = T(super.forkArgs() :+ "-Djdk.tls.client.protocols=TLSv1.2")
    override def ivyDeps  = super.ivyDeps() ++ tests
  }

  object jvm extends Shared {
    override def ivyDeps = super.ivyDeps() ++ http4s.jdkClient
    object test extends ScalaTests with SharedTestModule {
      override def ivyDeps  = super.ivyDeps() ++ tests ++ log4cats.logback
    }
  }

  object js extends Shared with ScalaJSModule {
    def scalaJSVersion = "1.14.0"
    override def ivyDeps = super.ivyDeps() ++ http4s.emberClient ++ scalajsJavaTime
    object test extends ScalaJSTests with SharedTestModule {
      override def ivyDeps  = super.ivyDeps() ++ tests ++ log4cats.jsConsole
      override def moduleKind = ModuleKind.CommonJSModule
    }
  }

  object native extends Shared with ScalaNativeModule {
    def scalaNativeVersion = "0.4.16"
    override def ivyDeps = super.ivyDeps() ++ http4s.emberClient
    object test extends ScalaNativeTests with SharedTestModule
  }
}
