package com.goyeau.kubernetes.client.operation

import cats.effect._
import cats.implicits._
import com.goyeau.kubernetes.client.api.NamespacesApiTest
import com.goyeau.kubernetes.client.{KubeConfig, KubernetesClient}
import io.chrisdavenport.log4cats.Logger
import java.io.File
import org.scalatest.{BeforeAndAfterAll, ParallelTestExecution, Suite}

trait MinikubeClientProvider[F[_]] extends BeforeAndAfterAll with ParallelTestExecution {
  this: Suite =>

  implicit def F: ConcurrentEffect[F]
  implicit def timer: Timer[F]
  implicit def contextShift: ContextShift[F]
  implicit def logger: Logger[F]

  val kubernetesClient: Resource[F, KubernetesClient[F]] = {
    val kubeConfig = KubeConfig.fromFile[F](
      new File(s"${System.getProperty("user.home")}/.kube/config"),
      sys.env.getOrElse("KUBE_CONTEXT_NAME", "minikube")
    )
    KubernetesClient(kubeConfig)
  }

  def resourceName: String

  private val createNamespace = kubernetesClient.use { implicit client =>
    for {
      _ <- client.namespaces.deleteTerminated(resourceName.toLowerCase)
      _ <- NamespacesApiTest.createChecked[F](resourceName.toLowerCase)
    } yield ()
  }

  private val deleteNamespace = kubernetesClient.use { client =>
    client.namespaces.delete(resourceName.toLowerCase).void
  }

  override def beforeAll(): Unit = ConcurrentEffect[F].toIO(createNamespace).unsafeRunSync()

  override def afterAll(): Unit = ConcurrentEffect[F].toIO(deleteNamespace).unsafeRunSync()

  def usingMinikube[T](body: KubernetesClient[F] => F[T]): T =
    ConcurrentEffect[F].toIO(kubernetesClient.use(body)).unsafeRunSync()
}
