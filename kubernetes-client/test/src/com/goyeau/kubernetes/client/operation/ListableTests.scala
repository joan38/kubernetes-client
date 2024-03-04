package com.goyeau.kubernetes.client.operation

import com.goyeau.kubernetes.client.MinikubeClientProvider
import cats.syntax.all.*
import cats.effect.*
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.api.NamespacesApiTest
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import scala.language.reflectiveCalls

trait ListableTests[R <: { def metadata: Option[ObjectMeta] }, ResourceList <: { def items: Seq[R] }] {
  self: MinikubeClientProvider =>

  val resourceIsNamespaced = true
  val namespaceResourceNames =
    (0 to 1).map(i => (s"${resourceName.toLowerCase}-$i-list", s"list-all-${resourceName.toLowerCase}-$i")).toSet

  override protected val extraNamespace = namespaceResourceNames.map(_._1).toList

  def api(implicit client: KubernetesClient[IO]): Listable[IO, ResourceList]
  def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Listable[IO, ResourceList]
  def createChecked(namespaceName: String, resourceName: String, labels: Map[String, String] = Map.empty)(implicit
      client: KubernetesClient[IO]
  ): IO[R]

  def listContains(namespaceName: String, resourceNames: Set[String], labels: Map[String, String] = Map.empty)(implicit
      client: KubernetesClient[IO]
  ): IO[ResourceList] =
    for {
      resourceList <- namespacedApi(namespaceName).list(labels)
      _ = assert(resourceNames.subsetOf(resourceList.items.flatMap(_.metadata.flatMap(_.name)).toSet))
    } yield resourceList

  def listAllContains(resourceNames: Set[String])(implicit
      client: KubernetesClient[IO]
  ): IO[ResourceList] =
    for {
      resourceList <- api.list()
      _ = assert(resourceNames.subsetOf(resourceList.items.flatMap(_.metadata.flatMap(_.name)).toSet))
    } yield resourceList

  def listNotContains(
      namespaceName: String,
      resourceNames: Set[String],
      labels: Map[String, String] = Map.empty
  )(implicit
      client: KubernetesClient[IO]
  ): IO[ResourceList] =
    for {
      resourceList <- namespacedApi(namespaceName).list(labels)
      names = resourceList.items.flatMap(_.metadata.flatMap(_.name))
      _ = assert(
        names.forall(!resourceNames.contains(_)),
        s"Actual names: $names, not expected names: $resourceNames, in namespace: $namespaceName, with labels: $labels"
      )
    } yield resourceList

  test(s"list ${resourceName}s") {
    usingMinikube { implicit client =>
      for {
        namespaceName <- IO.pure(resourceName.toLowerCase)
        resourceName  <- IO.pure("list-resource")
        _             <- listNotContains(namespaceName, Set(resourceName))
        _             <- createChecked(namespaceName, resourceName)
        _             <- listContains(namespaceName, Set(resourceName))
      } yield ()
    }
  }

  test(s"list ${resourceName}s with a label") {
    usingMinikube { implicit client =>
      for {
        namespaceName         <- IO.pure(resourceName.toLowerCase)
        noLabelResourceName   <- IO.pure("no-label-resource")
        _                     <- createChecked(namespaceName, noLabelResourceName)
        withLabelResourceName <- IO.pure("label-resource")
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
        _ <- namespaceResourceNames.toList.traverse { case (namespaceName, resourceName) =>
          client.namespaces.deleteTerminated(namespaceName) *> NamespacesApiTest.createChecked(
            namespaceName
          ) *> createChecked(
            namespaceName,
            resourceName
          )
        }
        _ <- listAllContains(namespaceResourceNames.map(_._2))
        _ <- namespaceResourceNames.toList.traverse { case (namespaceName, _) =>
          client.namespaces.delete(namespaceName)
        }
      } yield ()
    }
  }
}
