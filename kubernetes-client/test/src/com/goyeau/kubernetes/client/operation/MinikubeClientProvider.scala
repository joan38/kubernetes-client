package com.goyeau.kubernetes.client.operation

import cats.effect._
import cats.implicits._
import com.goyeau.kubernetes.client.api.NamespacesApiTest
import com.goyeau.kubernetes.client.{KubeConfig, KubernetesClient}
import munit.Suite
import org.typelevel.log4cats.Logger

import java.io.File

trait MinikubeClientProvider[F[_]] {
  this: Suite =>

  implicit def F: Async[F]
  implicit def logger: Logger[F]

  def unsafeRunSync[A](f: F[A]): A

  val kubernetesClient: Resource[F, KubernetesClient[F]] = {
    val kubeConfig = KubeConfig.fromFile[F](
      new File(s"${System.getProperty("user.home")}/.kube/config"),
      sys.env.getOrElse("KUBE_CONTEXT_NAME", "minikube")
    )
    KubernetesClient(kubeConfig)
  }

  def resourceName: String

  def defaultNamespace: String = resourceName.toLowerCase

  protected val extraNamespace = Option.empty[String]

  protected def createNamespace(namespace: String): F[Unit] = kubernetesClient.use { implicit client =>
    client.namespaces.deleteTerminated(namespace) *>
      NamespacesApiTest.createChecked[F](namespace)
  }.void

  private def deleteNamespace(namespace: String) = kubernetesClient.use { client =>
    client.namespaces.delete(namespace)
  }.void

  override def beforeAll(): Unit =
    (defaultNamespace +: extraNamespace.toList).foreach(name => unsafeRunSync(createNamespace(name)))

  override def afterAll(): Unit =
    (defaultNamespace +: extraNamespace.toList).foreach(name => unsafeRunSync(deleteNamespace(name)))

  def usingMinikube[T](body: KubernetesClient[F] => F[T]): T =
    unsafeRunSync(kubernetesClient.use(body))
}
