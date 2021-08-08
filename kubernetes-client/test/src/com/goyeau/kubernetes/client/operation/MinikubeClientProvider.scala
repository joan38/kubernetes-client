package com.goyeau.kubernetes.client.operation

import cats.effect._
import cats.implicits._
import com.goyeau.kubernetes.client.api.NamespacesApiTest
import com.goyeau.kubernetes.client.{KubeConfig, KubernetesClient}
import org.typelevel.log4cats.Logger
import java.io.File
import munit.Suite

trait MinikubeClientProvider[F[_]] {
  this: Suite =>

  def unsafeRunSync[A](f: F[A]): A

  implicit def F: Async[F]
  implicit def logger: Logger[F]

  val kubernetesClient: Resource[F, KubernetesClient[F]] = {
    val kubeConfig = KubeConfig.fromFile[F](
      new File(s"${System.getProperty("user.home")}/.kube/config"),
      sys.env.getOrElse("KUBE_CONTEXT_NAME", "minikube")
    )
    KubernetesClient(kubeConfig)
  }

  def resourceName: String

  private val createNamespace: F[Unit] = kubernetesClient.use { implicit client =>
    for {
      _ <- client.namespaces.deleteTerminated(resourceName.toLowerCase)
      _ <- NamespacesApiTest.createChecked[F](resourceName.toLowerCase)
    } yield ()
  }

  private val deleteNamespace = kubernetesClient.use { client =>
    client.namespaces.delete(resourceName.toLowerCase).void
  }

  override def beforeAll(): Unit = unsafeRunSync(createNamespace)

  override def afterAll(): Unit = unsafeRunSync(deleteNamespace)

  def usingMinikube[T](body: KubernetesClient[F] => F[T]): T =
    unsafeRunSync(kubernetesClient.use(body))
}
