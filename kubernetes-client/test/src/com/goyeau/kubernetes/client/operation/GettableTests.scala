package com.goyeau.kubernetes.client.operation

import com.goyeau.kubernetes.client.MinikubeClientProvider
import cats.effect.*
import com.goyeau.kubernetes.client.KubernetesClient
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s.client.UnexpectedStatus
import scala.language.reflectiveCalls

trait GettableTests[R <: { def metadata: Option[ObjectMeta] }] {
  self: MinikubeClientProvider =>

  def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Gettable[IO, R]
  def createChecked(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[IO]): IO[R]

  def getChecked(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[IO]): IO[R] =
    for {
      resource <- namespacedApi(namespaceName).get(resourceName)
      _ = assertEquals(resource.metadata.flatMap(_.namespace), Some(namespaceName))
      _ = assertEquals(resource.metadata.flatMap(_.name), Some(resourceName))
    } yield resource

  test(s"get a $resourceName") {
    usingMinikube { implicit client =>
      for {
        namespaceName <- IO.pure(resourceName.toLowerCase)
        resourceName  <- IO.pure("some-resource-get")
        _             <- createChecked(namespaceName, resourceName)
        _             <- getChecked(namespaceName, resourceName)
      } yield ()
    }
  }

  test("fail on non existing namespace") {
    usingMinikube(implicit client => getChecked("non-existing", "non-existing")).intercept[UnexpectedStatus]
  }

  test(s"fail on non existing $resourceName") {
      usingMinikube { implicit client =>
        for {
          namespaceName <- IO.pure(resourceName.toLowerCase)
          _             <- getChecked(namespaceName, "non-existing")
        } yield ()
      }.intercept[UnexpectedStatus]
    }
}
