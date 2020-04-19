package com.goyeau.kubernetes.client.operation

import cats.Applicative
import cats.implicits._
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.api.NamespacesApiTest
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers

import scala.language.reflectiveCalls

trait ListableTests[F[_], Resource <: { def metadata: Option[ObjectMeta] }, ResourceList <: { def items: Seq[Resource] }]
    extends AnyFlatSpec
    with Matchers
    with OptionValues
    with MinikubeClientProvider[F] {

  def api(implicit client: KubernetesClient[F]): Listable[F, ResourceList]
  def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[F]): Listable[F, ResourceList]
  def createChecked(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]): F[Resource]
  val resourceIsNamespaced = true

  def listContains(namespaceName: String, resourceNames: Seq[String])(
      implicit client: KubernetesClient[F]
  ): F[ResourceList] =
    for {
      resourceList <- namespacedApi(namespaceName).list
      _ = (resourceList.items.map(_.metadata.value.name.value) should contain).allElementsOf(resourceNames)
    } yield resourceList

  def listAllContains(resourceNames: Seq[String])(
      implicit client: KubernetesClient[F]
  ): F[ResourceList] =
    for {
      resourceList <- api.list
      _ = (resourceList.items.map(_.metadata.value.name.value) should contain).allElementsOf(resourceNames)
    } yield resourceList

  def listNotContains(namespaceName: String, resourceNames: Seq[String])(
      implicit client: KubernetesClient[F]
  ): F[ResourceList] =
    for {
      resourceList <- namespacedApi(namespaceName).list
      _ = (resourceList.items.map(_.metadata.value.name.value) should contain).noElementsOf(resourceNames)
    } yield resourceList

  "list" should s"list ${resourceName}s" in usingMinikube { implicit client =>
    for {
      namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
      resourceName  <- Applicative[F].pure("list-resource")
      _             <- listNotContains(namespaceName, Seq(resourceName))
      _             <- createChecked(namespaceName, resourceName)
      _             <- listContains(namespaceName, Seq(resourceName))
    } yield ()
  }

  it should s"list ${resourceName}s in all namespaces" in usingMinikube { implicit client =>
    assume(resourceIsNamespaced)
    for {
      namespaceResourceNames <- Applicative[F].pure(
        (0 to 1).map(i => (s"${resourceName.toLowerCase}-$i", s"list-all-${resourceName.toLowerCase}-$i"))
      )
      _ <- namespaceResourceNames.toList.traverse {
        case (namespaceName, resourceName) =>
          NamespacesApiTest.createChecked[F](namespaceName) *> createChecked(namespaceName, resourceName)
      }
      _ <- listAllContains(namespaceResourceNames.map(_._2))
      _ <- namespaceResourceNames.toList.traverse {
        case (namespaceName, _) => client.namespaces.delete(namespaceName)
      }
    } yield ()
  }
}
