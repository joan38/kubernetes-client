package com.goyeau.kubernetes.client.operation

import cats.Applicative
import cats.implicits._
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.Utils.retry
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s.Status
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Assertion, OptionValues}

trait CreatableTests[F[_], Resource <: { def metadata: Option[ObjectMeta] }]
    extends AnyFlatSpec
    with Matchers
    with OptionValues
    with MinikubeClientProvider[F] {

  def namespacedApi(namespaceName: String, labels: Map[String, String] = Map.empty)(
      implicit client: KubernetesClient[F]
  ): Creatable[F, Resource]
  def getChecked(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]): F[Resource]
  def sampleResource(resourceName: String, labels: Map[String, String] = Map.empty): Resource
  def modifyResource(resource: Resource): Resource
  def checkUpdated(updatedResource: Resource): Assertion

  def createChecked(namespaceName: String, resourceName: String)(
      implicit client: KubernetesClient[F]
  ): F[Resource] = createChecked(namespaceName, resourceName, Map.empty)

  def createChecked(namespaceName: String, resourceName: String, labels: Map[String, String])(
      implicit client: KubernetesClient[F]
  ): F[Resource] = {
    val resource = sampleResource(resourceName, labels)
    for {
      status <- namespacedApi(namespaceName).create(resource)
      _ = status shouldBe Status.Created
      resource <- getChecked(namespaceName, resourceName)
    } yield resource
  }

  "create" should s"create a $resourceName" in usingMinikube { implicit client =>
    createChecked(resourceName.toLowerCase, "create-resource")
  }

  "createOrUpdate" should s"create a $resourceName" in usingMinikube { implicit client =>
    for {
      namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
      resourceName = "create-update-resource"
      status <- namespacedApi(namespaceName).createOrUpdate(sampleResource(resourceName))
      _ = status shouldBe Status.Created
      _ <- getChecked(namespaceName, resourceName)
    } yield ()
  }

  def createOrUpdate(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]) =
    for {
      resource <- getChecked(namespaceName, resourceName)
      status   <- namespacedApi(namespaceName).createOrUpdate(modifyResource(resource))
      _ = status shouldBe Status.Ok
    } yield ()

  it should s"update a $resourceName already created" in usingMinikube { implicit client =>
    for {
      namespaceName   <- Applicative[F].pure(resourceName.toLowerCase)
      resourceName    <- Applicative[F].pure("update-resource")
      _               <- createChecked(namespaceName, resourceName)
      _               <- retry(createOrUpdate(namespaceName, resourceName))
      updatedResource <- getChecked(namespaceName, resourceName)
      _ = checkUpdated(updatedResource)
    } yield ()
  }
}
