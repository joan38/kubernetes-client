package com.goyeau.kubernetes.client.operation

import cats.Applicative
import cats.implicits._
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.Utils.retry
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import munit.FunSuite
import org.http4s.Status

trait CreatableTests[F[_], Resource <: { def metadata: Option[ObjectMeta] }]
    extends FunSuite
    with MinikubeClientProvider[F] {

  def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[F]): Creatable[F, Resource]
  def getChecked(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]): F[Resource]
  def sampleResource(resourceName: String, labels: Map[String, String] = Map.empty): Resource
  def modifyResource(resource: Resource): Resource
  def checkUpdated(updatedResource: Resource): Unit

  def createChecked(namespaceName: String, resourceName: String)(implicit
      client: KubernetesClient[F]
  ): F[Resource] = createChecked(namespaceName, resourceName, Map.empty)

  def createCheckedReturning(namespaceName: String, resourceName: String)(implicit
      client: KubernetesClient[F]
  ): F[Resource] = createCheckedReturning(namespaceName, resourceName, Map.empty)

  def createChecked(namespaceName: String, resourceName: String, labels: Map[String, String])(implicit
      client: KubernetesClient[F]
  ): F[Resource] = {
    val resource = sampleResource(resourceName, labels)
    for {
      status <- namespacedApi(namespaceName).create(resource)
      _ = assertEquals(status, Status.Created)
      resource <- getChecked(namespaceName, resourceName)
    } yield resource
  }

  def createCheckedReturning(namespaceName: String, resourceName: String, labels: Map[String, String])(implicit
      client: KubernetesClient[F]
  ): F[Resource] = {
    val resource = sampleResource(resourceName, labels)
    for {
      createdResource   <- namespacedApi(namespaceName).createReturningResource(resource)
      retrievedResource <- getChecked(namespaceName, resourceName)
      _ = assertEquals(createdResource, retrievedResource)
    } yield retrievedResource
  }

  test(s"create a $resourceName") {
    usingMinikube { implicit client =>
      createChecked(resourceName.toLowerCase, "create-resource")
    }
  }

  test(s"create a $resourceName returning resource") {
    usingMinikube { implicit client =>
      createCheckedReturning(resourceName.toLowerCase, "create-resource-1")
    }
  }

  test(s"create a $resourceName") {
    usingMinikube { implicit client =>
      for {
        namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
        resourceName = "create-update-resource"
        status <- namespacedApi(namespaceName).createOrUpdate(sampleResource(resourceName))
        _ = assertEquals(status, Status.Created)
        _ <- getChecked(namespaceName, resourceName)
      } yield ()
    }
  }

  test(s"create a $resourceName returning resource") {
    usingMinikube { implicit client =>
      for {
        namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
        resourceName = "create-update-resource-1"
        createdResource   <- namespacedApi(namespaceName).createOrUpdateReturningResource(sampleResource(resourceName))
        retrievedResource <- getChecked(namespaceName, resourceName)
        _ = assertEquals(createdResource, retrievedResource)
      } yield ()
    }
  }

  def createOrUpdate(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]) =
    for {
      resource <- getChecked(namespaceName, resourceName)
      status   <- namespacedApi(namespaceName).createOrUpdate(modifyResource(resource))
      _ = assertEquals(status, Status.Ok)
    } yield ()

  test(s"update a $resourceName already created") {
    usingMinikube { implicit client =>
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

  def createOrUpdateReturningResource(namespaceName: String, resourceName: String)(implicit
      client: KubernetesClient[F]
  ) =
    for {
      retrievedResource <- getChecked(namespaceName, resourceName)
      updatedResource <- namespacedApi(namespaceName).createOrUpdateReturningResource(modifyResource(retrievedResource))
    } yield updatedResource

  test(s"update a $resourceName already created returning resource") {
    usingMinikube { implicit client =>
      for {
        namespaceName     <- Applicative[F].pure(resourceName.toLowerCase)
        resourceName      <- Applicative[F].pure("update-resource-1")
        _                 <- createChecked(namespaceName, resourceName)
        updatedResource   <- retry(createOrUpdateReturningResource(namespaceName, resourceName))
        retrievedResource <- getChecked(namespaceName, resourceName)
        _ = checkUpdated(retrievedResource)
        _ = assertEquals(updatedResource, retrievedResource)
      } yield ()
    }
  }
}
