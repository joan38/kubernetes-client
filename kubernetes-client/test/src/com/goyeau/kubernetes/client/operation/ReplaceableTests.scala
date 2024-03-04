package com.goyeau.kubernetes.client.operation

import com.goyeau.kubernetes.client.MinikubeClientProvider
import cats.effect.*
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.Utils.retry
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s.Status

trait ReplaceableTests[R <: { def metadata: Option[ObjectMeta] }] {
  self: MinikubeClientProvider =>

  def namespacedApi(namespaceName: String)(implicit
      client: KubernetesClient[IO]
  ): Replaceable[IO, R]
  def createChecked(namespaceName: String, resourceName: String)(implicit
      client: KubernetesClient[IO]
  ): IO[R]
  def getChecked(namespaceName: String, resourceName: String)(implicit
      client: KubernetesClient[IO]
  ): IO[R]
  def sampleResource(resourceName: String, labels: Map[String, String] = Map.empty): R
  def modifyResource(resource: R): R
  def checkUpdated(updatedResource: R): Unit

  def replace(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[IO]) =
    for {
      resource <- getChecked(namespaceName, resourceName)
      status   <- namespacedApi(namespaceName).replace(modifyResource(resource))
      _ = assertEquals(status, Status.Ok)
    } yield ()

  def replaceWithResource(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[IO]) =
    for {
      resource         <- getChecked(namespaceName, resourceName)
      replacedResource <- namespacedApi(namespaceName).replaceWithResource(modifyResource(resource))
    } yield replacedResource

  test(s"replace a $resourceName") {
    usingMinikube { implicit client =>
      for {
        namespaceName <- IO.pure(resourceName.toLowerCase)
        resourceName  <- IO.pure("some-resource")
        _             <- createChecked(namespaceName, resourceName)
        _             <- retry(replace(namespaceName, resourceName), actionClue = Some("Replacing resource"))
        replaced      <- getChecked(namespaceName, resourceName)
        _ = checkUpdated(replaced)
      } yield ()
    }
  }

  test(s"replace a $resourceName with resource") {
    usingMinikube { implicit client =>
      for {
        namespaceName    <- IO.pure(resourceName.toLowerCase)
        resourceName     <- IO.pure("some-with-resource")
        _                <- createChecked(namespaceName, resourceName)
        replacedResource <- retry(replaceWithResource(namespaceName, resourceName), actionClue = Some("Replacing resource with resource"))
        _ = checkUpdated(replacedResource)
        retrievedResource <- getChecked(namespaceName, resourceName)
        _ = checkUpdated(retrievedResource)
      } yield ()
    }
  }

  test("fail on non existing namespace") {
    usingMinikube { implicit client =>
      for {
        status <- namespacedApi("non-existing").replace(sampleResource("non-existing"))
        _ = assertEquals(status, Status.NotFound)
      } yield ()
    }
  }

  // Returns Created status since Kubernetes 1.23.x, earlier versions return NotFound
  test(s"fail on non existing $resourceName") {
    usingMinikube { implicit client =>
      for {
        namespaceName <- IO.pure(resourceName.toLowerCase)
        status        <- namespacedApi(namespaceName).replace(sampleResource("non-existing"))
        _ = assert(Set(Status.NotFound, Status.Created).contains(status))
      } yield ()
    }
  }
}
