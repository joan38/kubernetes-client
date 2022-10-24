package com.goyeau.kubernetes.client.operation

import cats.Applicative
import cats.implicits.*
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.Utils.*
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import munit.FunSuite
import org.http4s.Status

trait DeletableTests[F[
    _
], Resource <: { def metadata: Option[ObjectMeta] }, ResourceList <: { def items: Seq[Resource] }]
    extends FunSuite
    with MinikubeClientProvider[F] {

  def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[F]): Deletable[F]
  def createChecked(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]): F[Resource]
  def listNotContains(namespaceName: String, resourceNames: Set[String], labels: Map[String, String] = Map.empty)(
      implicit client: KubernetesClient[F]
  ): F[ResourceList]
  def delete(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]): F[Status] =
    namespacedApi(namespaceName).delete(resourceName)

  test(s"delete a $resourceName") {
    usingMinikube { implicit client =>
      for {
        namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
        resourceName  <- Applicative[F].pure("delete-resource")
        _             <- createChecked(namespaceName, resourceName)
        _             <- delete(namespaceName, resourceName)
        _             <- retry(listNotContains(namespaceName, Set(resourceName)))
      } yield ()
    }
  }

  test("fail on non existing namespace") {
    usingMinikube { implicit client =>
      for {
        status <- delete("non-existing", "non-existing")
        _ = assertEquals(status, Status.NotFound)
      } yield ()
    }
  }

//  This test seem to yield Ok status since Kubernetes 1.23.x, are we trying to be idempotent now?
//  test(s"fail on non existing $resourceName") {
//    usingMinikube { implicit client =>
//      for {
//        namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
//        status        <- delete(namespaceName, "non-existing")
//        _ = assertEquals(status, Status.NotFound)
//      } yield ()
//    }
//  }
}
