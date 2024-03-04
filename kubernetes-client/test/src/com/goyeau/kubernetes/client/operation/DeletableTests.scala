package com.goyeau.kubernetes.client.operation

import com.goyeau.kubernetes.client.MinikubeClientProvider
import cats.syntax.all.*
import cats.effect.*
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.Utils.*
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s.Status
import io.k8s.apimachinery.pkg.apis.meta.v1.DeleteOptions

trait DeletableTests[R <: { def metadata: Option[ObjectMeta] }, ResourceList <: { def items: Seq[R] }] {
  self: MinikubeClientProvider =>

  def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO]
  def createChecked(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[IO]): IO[R]
  def listNotContains(namespaceName: String, resourceNames: Set[String], labels: Map[String, String] = Map.empty)(
      implicit client: KubernetesClient[IO]
  ): IO[ResourceList]
  def delete(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[IO]): IO[Status] = {
    val deleteOptions = DeleteOptions(gracePeriodSeconds = Some(0L))
    namespacedApi(namespaceName).delete(resourceName, deleteOptions.some)
  }

  test(s"delete a $resourceName") {
    usingMinikube { implicit client =>
      for {
        namespaceName <- IO.pure(resourceName.toLowerCase)
        resourceName  <- IO.pure("delete-resource")
        _             <- createChecked(namespaceName, resourceName)
        status        <- delete(namespaceName, resourceName)
        //  returns Ok status since Kubernetes 1.23.x, earlier versions return NotFound
        _ = assert(Set(Status.NotFound, Status.Ok).contains(status))
        _ <- retry(
          listNotContains(namespaceName, Set(resourceName)),
          actionClue = Some(s"List not contains: $resourceName")
        )
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

  test(s"fail on non existing $resourceName") {
    usingMinikube { implicit client =>
      for {
        namespaceName <- IO.pure(resourceName.toLowerCase)
        status        <- delete(namespaceName, "non-existing")
        //  returns Ok status since Kubernetes 1.23.x, earlier versions return NotFound
        _ = assert(Set(Status.NotFound, Status.Ok).contains(status))
      } yield ()
    }
  }
}
