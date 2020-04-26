package com.goyeau.kubernetes.client.operation

import cats.Applicative
import cats.implicits._
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.Utils._
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s.Status
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait DeletableTests[F[_], Resource <: { def metadata: Option[ObjectMeta] }, ResourceList <: { def items: Seq[Resource] }]
    extends AnyFlatSpec
    with Matchers
    with OptionValues
    with MinikubeClientProvider[F] {

  def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[F]): Deletable[F]
  def createChecked(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]): F[Resource]
  def listNotContains(namespaceName: String, resourceNames: Seq[String], labels: Map[String, String] = Map.empty)(
      implicit client: KubernetesClient[F]
  ): F[ResourceList]
  def delete(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]): F[Status] =
    namespacedApi(namespaceName).delete(resourceName)

  "delete" should s"delete a $resourceName" in usingMinikube { implicit client =>
    for {
      namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
      resourceName  <- Applicative[F].pure("delete-resource")
      _             <- createChecked(namespaceName, resourceName)
      _             <- delete(namespaceName, resourceName)
      _             <- retry(listNotContains(namespaceName, Seq(resourceName)))
    } yield ()
  }

  it should "fail on non existing namespace" in usingMinikube { implicit client =>
    for {
      status <- delete("non-existing", "non-existing")
      _ = status shouldBe Status.NotFound
    } yield ()
  }

  it should s"fail on non existing $resourceName" in usingMinikube { implicit client =>
    for {
      namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
      status        <- delete(namespaceName, "non-existing")
      _ = status shouldBe Status.NotFound
    } yield ()
  }
}
