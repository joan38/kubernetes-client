package com.goyeau.kubernetes.client.operation

import cats.Applicative
import cats.implicits._
import com.goyeau.kubernetes.client.KubernetesClient
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import munit.FunSuite
import org.http4s.client.UnexpectedStatus
import scala.language.reflectiveCalls

trait GettableTests[F[_], Resource <: { def metadata: Option[ObjectMeta] }]
    extends FunSuite
    with MinikubeClientProvider[F] {

  def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[F]): Gettable[F, Resource]
  def createChecked(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]): F[Resource]

  def getChecked(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]): F[Resource] =
    for {
      resource <- namespacedApi(namespaceName).get(resourceName)
      _ = assertEquals(resource.metadata.flatMap(_.namespace), Some(namespaceName))
      _ = assertEquals(resource.metadata.flatMap(_.name), Some(resourceName))
    } yield resource

  test(s"get a $resourceName") {
    usingMinikube { implicit client =>
      for {
        namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
        resourceName  <- Applicative[F].pure("some-resource-get")
        _             <- createChecked(namespaceName, resourceName)
        _             <- getChecked(namespaceName, resourceName)
      } yield ()
    }
  }

  test("fail on non existing namespace") {
    intercept[UnexpectedStatus] {
      usingMinikube(implicit client => getChecked("non-existing", "non-existing"))
    }
  }

  test(s"fail on non existing $resourceName") {
    intercept[UnexpectedStatus] {
      usingMinikube { implicit client =>
        for {
          namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
          _             <- getChecked(namespaceName, "non-existing")
        } yield ()
      }
    }
  }
}
