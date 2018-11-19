package com.goyeau.kubernetes.client.operation

import cats.Applicative
import cats.implicits._
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.api.NamespacesApiTest
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s.Status
import org.scalatest.{FlatSpec, Matchers, OptionValues}

trait DeletableTests[F[_], Resource <: { def metadata: Option[ObjectMeta] }, ResourceList <: { def items: Seq[Resource] }]
    extends FlatSpec
    with Matchers
    with OptionValues
    with MinikubeClientProvider[F] {

  def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[F]): Deletable[F]
  def createChecked(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]): F[Resource]
  def listNotContains(namespaceName: String, resourceNames: Seq[String])(
    implicit client: KubernetesClient[F]
  ): F[ResourceList]

  "delete" should s"delete a $resourceName" in usingMinikube { implicit client =>
    def checkEventuallyDeleted(namespaceName: String, resourceName: String): F[ResourceList] =
      listNotContains(namespaceName, Seq(resourceName))
        .handleErrorWith(_ => checkEventuallyDeleted(namespaceName, resourceName))

    for {
      namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
      deploymentName <- Applicative[F].pure("some-resource")
      _ <- createChecked(namespaceName, deploymentName)

      _ <- namespacedApi(namespaceName).delete(deploymentName)
      _ <- checkEventuallyDeleted(namespaceName, deploymentName)
    } yield ()
  }

  it should "fail on non existing namespace" in usingMinikube { implicit client =>
    for {
      status <- namespacedApi("non-existing").delete("non-existing")
      _ = status shouldBe Status.NotFound
    } yield ()
  }

  it should s"fail on non existing $resourceName" in usingMinikube { implicit client =>
    for {
      namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
      _ <- NamespacesApiTest.createChecked(namespaceName)

      status <- namespacedApi(namespaceName).delete("non-existing")
      _ = status shouldBe Status.NotFound
    } yield ()
  }
}
