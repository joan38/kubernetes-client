package com.goyeau.kubernetes.client.operation

import com.goyeau.kubernetes.client.MinikubeClientProvider
import cats.effect.*
import com.goyeau.kubernetes.client.KubernetesClient
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s.Status

trait DeletableTerminatedTests[R <: { def metadata: Option[ObjectMeta] }, ResourceList <: { def items: Seq[R] }] {
    self: MinikubeClientProvider =>

  def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]): DeletableTerminated[IO]
  def createChecked(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[IO]): IO[R]
  def listNotContains(namespaceName: String, resourceNames: Set[String], labels: Map[String, String] = Map.empty)(
      implicit client: KubernetesClient[IO]
  ): IO[ResourceList]
  def deleteTerminated(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[IO]): IO[Status] =
    namespacedApi(namespaceName).deleteTerminated(resourceName)

  test(s"delete $resourceName and block until fully deleted") {
    usingMinikube { implicit client =>
      for {
        namespaceName <- IO.pure(resourceName.toLowerCase)
        resourceName  <- IO.pure("delete-terminated-resource")
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
        namespaceName <- IO.pure(resourceName.toLowerCase)
        status        <- deleteTerminated(namespaceName, "non-existing")
        //  returns Ok status since Kubernetes 1.23.x, earlier versions return NotFound
        _ = assert(Set(Status.NotFound, Status.Ok).contains(status))
      } yield ()
    }
  }
}
