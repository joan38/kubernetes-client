package com.goyeau.kubernetes.client.operation

import cats.Applicative
import cats.implicits.*
import com.goyeau.kubernetes.client.KubernetesClient
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import munit.FunSuite
import org.http4s.Status

trait DeletableTerminatedTests[F[
    _
], Resource <: { def metadata: Option[ObjectMeta] }, ResourceList <: { def items: Seq[Resource] }]
    extends FunSuite
    with MinikubeClientProvider[F] {

  def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[F]): DeletableTerminated[F]
  def createChecked(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]): F[Resource]
  def listNotContains(namespaceName: String, resourceNames: Set[String], labels: Map[String, String] = Map.empty)(
      implicit client: KubernetesClient[F]
  ): F[ResourceList]
  def deleteTerminated(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]): F[Status] =
    namespacedApi(namespaceName).deleteTerminated(resourceName)

  test(s"delete $resourceName and block until fully deleted") {
    usingMinikube { implicit client =>
      for {
        namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
        resourceName  <- Applicative[F].pure("delete-terminated-resource")
        _             <- deleteTerminated(namespaceName, resourceName)
        _             <- listNotContains(namespaceName, Set(resourceName))
      } yield ()
    }
  }

  test("fail on non existing namespace") {
    usingMinikube { implicit client =>
      for {
        status <- deleteTerminated("non-existing", "non-existing")
        _ = assertEquals(status, Status.NotFound)
      } yield ()
    }
  }

  test(s"fail on non existing $resourceName") {
    usingMinikube { implicit client =>
      for {
        namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
        status        <- deleteTerminated(namespaceName, "non-existing")
        _ = assertEquals(status, Status.NotFound)
      } yield ()
    }
  }
}
