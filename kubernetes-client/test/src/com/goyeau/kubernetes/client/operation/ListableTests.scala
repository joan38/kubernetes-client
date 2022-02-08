package com.goyeau.kubernetes.client.operation

import cats.Applicative
import cats.implicits.*
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.api.NamespacesApiTest
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import munit.FunSuite
import scala.language.reflectiveCalls

trait ListableTests[F[
    _
], Resource <: { def metadata: Option[ObjectMeta] }, ResourceList <: { def items: Seq[Resource] }]
    extends FunSuite
    with MinikubeClientProvider[F] {

  val resourceIsNamespaced = true

  def api(implicit client: KubernetesClient[F]): Listable[F, ResourceList]
  def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[F]): Listable[F, ResourceList]
  def createChecked(namespaceName: String, resourceName: String, labels: Map[String, String] = Map.empty)(implicit
      client: KubernetesClient[F]
  ): F[Resource]

  def listContains(namespaceName: String, resourceNames: Set[String], labels: Map[String, String] = Map.empty)(implicit
      client: KubernetesClient[F]
  ): F[ResourceList] =
    for {
      resourceList <- namespacedApi(namespaceName).list(labels)
      _ = assert(resourceNames.subsetOf(resourceList.items.flatMap(_.metadata.flatMap(_.name)).toSet))
    } yield resourceList

  def listAllContains(resourceNames: Set[String])(implicit
      client: KubernetesClient[F]
  ): F[ResourceList] =
    for {
      resourceList <- api.list()
      _ = assert(resourceNames.subsetOf(resourceList.items.flatMap(_.metadata.flatMap(_.name)).toSet))
    } yield resourceList

  def listNotContains(namespaceName: String, resourceNames: Set[String], labels: Map[String, String] = Map.empty)(
      implicit client: KubernetesClient[F]
  ): F[ResourceList] =
    for {
      resourceList <- namespacedApi(namespaceName).list(labels)
      _ = assert(resourceList.items.flatMap(_.metadata.flatMap(_.name)).forall(!resourceNames.contains(_)))
    } yield resourceList

  test(s"list ${resourceName}s") {
    usingMinikube { implicit client =>
      for {
        namespaceName <- Applicative[F].pure(resourceName.toLowerCase)
        resourceName  <- Applicative[F].pure("list-resource")
        _             <- listNotContains(namespaceName, Set(resourceName))
        _             <- createChecked(namespaceName, resourceName)
        _             <- listContains(namespaceName, Set(resourceName))
      } yield ()
    }
  }

  test(s"list ${resourceName}s with a label") {
    usingMinikube { implicit client =>
      for {
        namespaceName         <- Applicative[F].pure(resourceName.toLowerCase)
        noLabelResourceName   <- Applicative[F].pure("no-label-resource")
        _                     <- createChecked(namespaceName, noLabelResourceName)
        withLabelResourceName <- Applicative[F].pure("label-resource")
        labels = Map("test" -> "1")
        _ <- createChecked(namespaceName, withLabelResourceName, labels)
        _ <- listNotContains(namespaceName, Set(noLabelResourceName), labels)
        _ <- listContains(namespaceName, Set(withLabelResourceName), labels)
      } yield ()
    }
  }

  test(s"list ${resourceName}s in all namespaces") {
    usingMinikube { implicit client =>
      assume(resourceIsNamespaced)
      for {
        namespaceResourceNames <- Applicative[F].pure(
          (0 to 1).map(i => (s"${resourceName.toLowerCase}-$i", s"list-all-${resourceName.toLowerCase}-$i")).toSet
        )
        _ <- namespaceResourceNames.toList.traverse { case (namespaceName, resourceName) =>
          NamespacesApiTest.createChecked[F](namespaceName) *> createChecked(namespaceName, resourceName)
        }
        _ <- listAllContains(namespaceResourceNames.map(_._2))
        _ <- namespaceResourceNames.toList.traverse { case (namespaceName, _) =>
          client.namespaces.delete(namespaceName)
        }
      } yield ()
    }
  }
}
