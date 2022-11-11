package com.goyeau.kubernetes.client.operation

import cats.Applicative
import cats.implicits._
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.Utils.retry
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import munit.FunSuite
import org.http4s.Status

trait ReplaceableTests[F[_], Resource <: { def metadata: Option[ObjectMeta] }]
    extends FunSuite
    with MinikubeClientProvider[F] {

  def namespacedApi(namespaceName: String)(implicit
      client: KubernetesClient[F]
  ): Replaceable[F, Resource]
  def createChecked(namespaceName: String, resourceName: String)(implicit
      client: KubernetesClient[F]
  ): F[Resource]
  def getChecked(namespaceName: String, resourceName: String)(implicit
      client: KubernetesClient[F]
  ): F[Resource]
  def sampleResource(resourceName: String, labels: Map[String, String] = Map.empty): Resource
  def modifyResource(resource: Resource): Resource
  def checkUpdated(updatedResource: Resource): Unit

  def replace(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]) =
    for {
      resource <- getChecked(namespaceName, resourceName)
      status   <- namespacedApi(namespaceName).replace(modifyResource(resource))
      _ = assertEquals(status, Status.Ok)
    } yield ()

  def replaceWithResource(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]) =
    for {
      resource         <- getChecked(namespaceName, resourceName)
      replacedResource <- namespacedApi(namespaceName).replaceWithResource(modifyResource(resource))
    } yield replacedResource

  test(s"replace a $resourceName") {
    usingMinikube { implicit client =>
      for {
        namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
        resourceName  <- Applicative[F].pure("some-resource")
        _             <- createChecked(namespaceName, resourceName)
        _             <- retry(replace(namespaceName, resourceName))
        replaced      <- getChecked(namespaceName, resourceName)
        _ = checkUpdated(replaced)
      } yield ()
    }
  }

  test(s"replace a $resourceName with resource") {
    usingMinikube { implicit client =>
      for {
        namespaceName    <- Applicative[F].pure(resourceName.toLowerCase)
        resourceName     <- Applicative[F].pure("some-resource-1")
        _                <- createChecked(namespaceName, resourceName)
        replacedResource <- retry(replaceWithResource(namespaceName, resourceName))
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

//  This test seem to yield Created status since Kubernetes 1.23.x, are we trying to be idempotent now?
//  test(s"fail on non existing $resourceName") {
//    usingMinikube { implicit client =>
//      for {
//        namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
//        status        <- namespacedApi(namespaceName).replace(sampleResource("non-existing"))
//        _ = assert(Set(Status.NotFound, Status.InternalServerError).contains(status))
//      } yield ()
//    }
//  }
}
