package com.goyeau.kubernetes.client.operation

import cats.Applicative
import cats.implicits._
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.api.NamespacesApiTest
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s.Status
import org.scalatest.{FlatSpec, Matchers, OptionValues}

trait CreatableTests[F[_], Resource <: { def metadata: Option[ObjectMeta] }]
    extends FlatSpec
    with Matchers
    with OptionValues
    with MinikubeClientProvider[F] {

  def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[F]): Creatable[F, Resource]
  def getChecked(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]): F[Resource]
  def sampleResource(resourceName: String): Resource
  def modifyResource(resource: Resource): Resource
  def checkUpdated(updatedResource: Resource): Unit

  def createChecked(namespaceName: String, resourceName: String)(
    implicit client: KubernetesClient[F]
  ): F[Resource] =
    for {
      _ <- NamespacesApiTest.createChecked[F](namespaceName)
      status <- namespacedApi(namespaceName).create(sampleResource(resourceName))
      _ = status shouldBe Status.Created
      resource <- getChecked(namespaceName, resourceName)
    } yield resource

  "create" should s"create a $resourceName" in usingMinikube { implicit client =>
    createChecked(resourceName.toLowerCase, "some-resource")
  }

  "createOrUpdate" should s"create a $resourceName" in usingMinikube { implicit client =>
    for {
      namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
      _ <- NamespacesApiTest.createChecked(namespaceName)

      resourceName = "some-resource"
      status <- namespacedApi(namespaceName).createOrUpdate(sampleResource(resourceName))
      _ = status shouldBe Status.Created
      _ <- getChecked(namespaceName, resourceName)
    } yield ()
  }

  it should s"update a $resourceName already created" in usingMinikube { implicit client =>
    for {
      namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
      resourceName <- Applicative[F].pure("some-resource")
      resource <- createChecked(namespaceName, resourceName)

      status <- namespacedApi(namespaceName).createOrUpdate(modifyResource(resource))
      _ = status shouldBe Status.Ok
      updatedResource <- getChecked(namespaceName, resourceName)
      _ = checkUpdated(updatedResource)
    } yield ()
  }
}
