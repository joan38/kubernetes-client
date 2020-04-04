package com.goyeau.kubernetes.client.operation

import cats.Applicative
import cats.implicits._
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.api.NamespacesApiTest
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s.Status
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers

trait DeletableTerminatedTests[F[_], Resource <: { def metadata: Option[ObjectMeta] }, ResourceList <: { def items: Seq[Resource] }]
    extends AnyFlatSpec
    with Matchers
    with OptionValues
    with MinikubeClientProvider[F] {

  def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[F]): DeletableTerminated[F]
  def createChecked(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]): F[Resource]
  def listNotContains(namespaceName: String, resourceNames: Seq[String])(
    implicit client: KubernetesClient[F]
  ): F[ResourceList]

  "deleteTerminated" should s"delete $resourceName and block until fully deleted" in usingMinikube { implicit client =>
    for {
      namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
      resourceName <- Applicative[F].pure("some-resource")
      _ <- createChecked(namespaceName, resourceName)
      _ <- namespacedApi(namespaceName).deleteTerminated(resourceName)
      _ <- listNotContains(namespaceName, Seq(resourceName))
    } yield ()
  }

  it should "fail on non existing namespace" in usingMinikube { implicit client =>
    for {
      status <- namespacedApi("non-existing").deleteTerminated("non-existing")
      _ = status shouldBe Status.NotFound
    } yield ()
  }

  it should s"fail on non existing $resourceName" in usingMinikube { implicit client =>
    for {
      namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
      _ <- NamespacesApiTest.createChecked(namespaceName)
      status <- namespacedApi(namespaceName).deleteTerminated("non-existing")
      _ = status shouldBe Status.NotFound
    } yield ()
  }
}
