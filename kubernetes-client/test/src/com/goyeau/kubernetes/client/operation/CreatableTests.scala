package com.goyeau.kubernetes.client.operation

import cats.Applicative
import cats.implicits.*
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

  def createChecked(namespaceName: String, resourceName: String, labels: Map[String, String])(implicit
      client: KubernetesClient[F]
  ): F[Resource] = {
    val resource = sampleResource(resourceName, labels)
    for {
      status <- namespacedApi(namespaceName).create(resource)
      _      <- logger.info(s"Created '$resourceName' in $namespaceName namespace: $status")
      _      <- F.delay(assertEquals(status.isSuccess, true, s"$status should be successful"))
      resource <- retry(
        getChecked(namespaceName, resourceName),
        actionClue = Some(s"Getting after create '$resourceName' in $namespaceName namespace"),
        maxRetries = 2
      )
    } yield resource
  }

  def createWithResourceChecked(namespaceName: String, resourceName: String, labels: Map[String, String] = Map.empty)(
      implicit client: KubernetesClient[F]
  ): F[Resource] = {
    val resource = sampleResource(resourceName, labels)
    for {
      _                 <- namespacedApi(namespaceName).createWithResource(resource)
      retrievedResource <- getChecked(namespaceName, resourceName)
    } yield retrievedResource
  }

  test(s"create a $resourceName") {
    usingMinikube { implicit client =>
      createChecked(resourceName.toLowerCase, "create-resource")
    }
  }

  test(s"create a $resourceName with resource") {
    usingMinikube { implicit client =>
      createWithResourceChecked(resourceName.toLowerCase, "create-with-resource")
    }
  }

  test(s"create a $resourceName") {
    usingMinikube { implicit client =>
      for {
        namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
        resourceName = "create-update-resource"
        status <- namespacedApi(namespaceName).createOrUpdate(sampleResource(resourceName))
        _ = assert(Set(Status.Created, Status.Ok).contains(status), status.sanitizedReason)
        _ <- getChecked(namespaceName, resourceName)
      } yield ()
    }
  }

  test(s"create a $resourceName with resource") {
    usingMinikube { implicit client =>
      for {
        namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
        resourceName = "create-update-with-resource"
        _ <- namespacedApi(namespaceName).createOrUpdateWithResource(sampleResource(resourceName))
        _ <- getChecked(namespaceName, resourceName)
      } yield ()
    }
  }

  def createOrUpdate(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]): F[Unit] =
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
        _               <- retry(createOrUpdate(namespaceName, resourceName), actionClue = Some("Updating resource"))
        updatedResource <- getChecked(namespaceName, resourceName)
        _ = checkUpdated(updatedResource)
      } yield ()
    }
  }

  def createOrUpdateWithResource(namespaceName: String, resourceName: String)(implicit
      client: KubernetesClient[F]
  ) =
    for {
      retrievedResource <- getChecked(namespaceName, resourceName)
      updatedResource   <- namespacedApi(namespaceName).createOrUpdateWithResource(modifyResource(retrievedResource))
    } yield updatedResource

  test(s"update a $resourceName already created with resource") {
    usingMinikube { implicit client =>
      for {
        namespaceName   <- Applicative[F].pure(resourceName.toLowerCase)
        resourceName    <- Applicative[F].pure("update-with-resource")
        _               <- createChecked(namespaceName, resourceName)
        updatedResource <- retry(createOrUpdateWithResource(namespaceName, resourceName))
        _ = checkUpdated(updatedResource)
        retrievedResource <- getChecked(namespaceName, resourceName)
        _ = checkUpdated(retrievedResource)
      } yield ()
    }
  }
}
