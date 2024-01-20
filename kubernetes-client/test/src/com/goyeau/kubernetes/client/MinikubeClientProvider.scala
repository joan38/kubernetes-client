package com.goyeau.kubernetes.client

import cats.syntax.all.*
import cats.effect.*
import cats.effect.std.Env
import com.goyeau.kubernetes.client.Utils.retry
import com.goyeau.kubernetes.client.api.NamespacesApiTest
import com.goyeau.kubernetes.client.KubernetesClient
import munit.CatsEffectSuite
import org.typelevel.log4cats.Logger
import io.k8s.apimachinery.pkg.apis.meta.v1.DeleteOptions
import munit.catseffect.IOFixture

abstract class MinikubeClientProvider extends CatsEffectSuite {

  implicit def logger: Logger[IO]

  val kubernetesClient: Resource[IO, KubernetesClient[IO]] = TestPlatformSpecific.mkClient

  def resourceName: String

  def defaultNamespace: String = resourceName.toLowerCase

  protected val extraNamespace = List.empty[String]

  protected def createNamespace(namespace: String): IO[Unit] = kubernetesClient.use { implicit client =>
    client.namespaces.deleteTerminated(namespace) *> retry(
      NamespacesApiTest.createChecked(namespace),
      actionClue = Some(s"Creating '$namespace' namespace")
    )
  }.void

  private def deleteNamespace(namespace: String) = kubernetesClient.use { client =>
    client.namespaces.delete(
      namespace,
      DeleteOptions(gracePeriodSeconds = 0L.some, propagationPolicy = "Foreground".some).some
    )
  }.void

  protected def createNamespaces(): IO[Unit] = {
    val ns = defaultNamespace +: extraNamespace
    logger.info(s"Creating namespaces: $ns") *>
      ns.traverse_(name => createNamespace(name))
  }

  def usingMinikube[T](body: KubernetesClient[IO] => IO[T]): IO[T] =
    kubernetesClient.use(body)

  override def munitFixtures: Seq[IOFixture[Unit]] = List(
    ResourceSuiteLocalFixture(
      name = "namespaces",
      Resource.make(
        createNamespaces()
      ) { _ =>
        val ns = defaultNamespace +: extraNamespace
        logger.info(s"Deleting namespaces: $ns") *>
          ns.traverse_(name => deleteNamespace(name))
      }
    )
  )

}