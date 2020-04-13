package com.goyeau.kubernetes.client.operation

import cats.Applicative
import cats.implicits._
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.api.NamespacesApiTest
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s.client.UnexpectedStatus
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers

import scala.language.reflectiveCalls

trait GettableTests[F[_], Resource <: { def metadata: Option[ObjectMeta] }]
    extends AnyFlatSpec
    with Matchers
    with OptionValues
    with MinikubeClientProvider[F] {

  def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[F]): Gettable[F, Resource]
  def createChecked(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]): F[Resource]

  def getChecked(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]): F[Resource] =
    for {
      resource <- namespacedApi(namespaceName).get(resourceName)
      _ = resource.metadata.value.namespace.value shouldBe namespaceName
      _ = resource.metadata.value.name.value shouldBe resourceName
    } yield resource

  "get" should s"get a $resourceName" in usingMinikube { implicit client =>
    for {
      namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
      resourceName  <- Applicative[F].pure("some-resource")
      _             <- createChecked(namespaceName, resourceName)

      _ <- getChecked(namespaceName, resourceName)
    } yield ()
  }

  it should "fail on non existing namespace" in intercept[UnexpectedStatus] {
    usingMinikube(implicit client => getChecked("non-existing", "non-existing"))
  }

  it should s"fail on non existing $resourceName" in intercept[UnexpectedStatus] {
    usingMinikube { implicit client =>
      for {
        namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
        _             <- NamespacesApiTest.createChecked(namespaceName)

        _ <- getChecked(namespaceName, "non-existing")
      } yield ()
    }
  }
}
