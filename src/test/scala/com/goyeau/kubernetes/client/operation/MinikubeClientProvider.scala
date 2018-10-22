package com.goyeau.kubernetes.client.operation

import java.io.File

import cats.effect._
import cats.implicits._
import com.goyeau.kubernetes.client.{KubeConfig, KubernetesClient}
import io.chrisdavenport.log4cats.Logger
import org.scalatest.{BeforeAndAfter, Suite}

trait MinikubeClientProvider[F[_]] extends BeforeAndAfter { this: Suite =>

  implicit def F: ConcurrentEffect[F]
  implicit def timer: Timer[F]
  implicit def contextShift: ContextShift[IO]
  implicit def logger: Logger[F]

  val kubernetesClient: Resource[F, KubernetesClient[F]] = {
    val kubeConfig = KubeConfig[F](new File(s"${System.getProperty("user.home")}/.kube/config"), "minikube")
    KubernetesClient(kubeConfig)
  }

  def resourceName: String
  private val clean = kubernetesClient
    .use { client =>
      for {
        namespaceList <- client.namespaces.list
        namespacesToDelete = namespaceList.items
          .map(_.metadata.get.name.get)
          .filter(_.startsWith(resourceName.toLowerCase))
        _ <- namespacesToDelete.toList.traverse(client.namespaces.deleteTerminated(_))
      } yield ()
    }

  before {
    ConcurrentEffect[F].toIO(clean).unsafeRunSync()
  }

  after {
    ConcurrentEffect[F].toIO(clean).unsafeRunSync()
  }

  def usingMinikube[T](body: KubernetesClient[F] => F[T]): T =
    ConcurrentEffect[F].toIO(kubernetesClient.use(body)).unsafeRunSync()
}
