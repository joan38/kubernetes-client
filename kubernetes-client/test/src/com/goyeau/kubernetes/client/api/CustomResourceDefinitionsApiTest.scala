package com.goyeau.kubernetes.client.api

import cats.effect._
import cats.syntax.option._
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.api.CustomResourceDefinitionsApiTest._
import com.goyeau.kubernetes.client.operation._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.k8s.apiextensionsapiserver.pkg.apis.apiextensions.v1._
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import munit.FunSuite
import org.http4s.Status

object CustomResourceDefinitionsApiTest {
  val versions: CustomResourceDefinitionVersion =
    CustomResourceDefinitionVersion(
      name = "v1",
      served = true,
      storage = true,
      schema = CustomResourceValidation(
        JSONSchemaProps(
          `type` = "object".some,
          properties = Map(
            "spec" -> JSONSchemaProps(
              `type` = "object".some,
              properties = Map(
                "cronSpec" -> JSONSchemaProps(`type` = "string".some),
                "image"    -> JSONSchemaProps(`type` = "string".some),
                "replicas" -> JSONSchemaProps(`type` = "integer".some)
              ).some
            ),
            "status" -> JSONSchemaProps(
              `type` = "object".some,
              properties = Map(
                "name" -> JSONSchemaProps(`type` = "string".some)
              ).some
            )
          ).some
        ).some
      ).some,
      subresources = CustomResourceSubresources(status = CustomResourceSubresourceStatus().some).some
    )
  val crdLabel = Map("test" -> "kubernetes-client")
  val group    = "kubernetes-client.goyeau.com"

  def plural(resourceName: String): String  = s"${resourceName.toLowerCase}s"
  def crdName(resourceName: String): String = s"${plural(resourceName)}.$group"
  def crd(resourceName: String, labels: Map[String, String]): CustomResourceDefinition = CustomResourceDefinition(
    spec = CustomResourceDefinitionSpec(
      group = group,
      scope = "Namespaced",
      names = CustomResourceDefinitionNames(
        plural(resourceName),
        resourceName
      ),
      versions = Seq(versions)
    ),
    apiVersion = "apiextensions.k8s.io/v1".some,
    metadata = ObjectMeta(
      name = crdName(resourceName).some,
      labels = (labels ++ crdLabel).some
    ).some
  )
}

class CustomResourceDefinitionsApiTest
    extends FunSuite
    with CreatableTests[IO, CustomResourceDefinition]
    with GettableTests[IO, CustomResourceDefinition]
    with ListableTests[IO, CustomResourceDefinition, CustomResourceDefinitionList]
    with ReplaceableTests[IO, CustomResourceDefinition]
    with DeletableTests[IO, CustomResourceDefinition, CustomResourceDefinitionList]
    with DeletableTerminatedTests[IO, CustomResourceDefinition, CustomResourceDefinitionList]
    with WatchableTests[IO, CustomResourceDefinition]
    with ContextProvider {

  implicit lazy val F: ConcurrentEffect[IO] = IO.ioConcurrentEffect
  implicit lazy val logger: Logger[IO]      = Slf4jLogger.getLogger[IO]
  lazy val resourceName: String             = classOf[CustomResourceDefinition].getSimpleName
  override val resourceIsNamespaced         = false

  override def api(implicit client: KubernetesClient[IO]) = client.customResourceDefinitions
  override def delete(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[IO]) =
    namespacedApi(namespaceName).delete(crdName(resourceName))
  override def deleteResource(namespaceName: String, resourceName: String)(
      implicit client: KubernetesClient[IO]
  ): IO[Status] =
    namespacedApi(namespaceName).delete(crdName(resourceName))
  override def deleteTerminated(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[IO]) =
    namespacedApi(namespaceName).deleteTerminated(crdName(resourceName))

  def listNotContains(resourceNames: Set[String], labels: Map[String, String])(
      implicit client: KubernetesClient[IO]
  ): IO[CustomResourceDefinitionList] =
    for {
      resourceList <- client.customResourceDefinitions.list(labels)
      _ = assert(resourceList.items.flatMap(_.metadata.flatMap(_.name)).forall(!resourceNames.map(crdName).contains(_)))
    } yield resourceList

  override def listContains(namespaceName: String, resourceNames: Set[String], labels: Map[String, String])(
      implicit client: KubernetesClient[IO]
  ): IO[CustomResourceDefinitionList] = listContains(resourceNames)

  def listContains(resourceNames: Set[String])(
      implicit client: KubernetesClient[IO]
  ): IO[CustomResourceDefinitionList] =
    for {
      resourceList <- client.customResourceDefinitions.list()
      _ = assert(resourceNames.map(crdName).subsetOf(resourceList.items.flatMap(_.metadata.flatMap(_.name)).toSet))
    } yield resourceList

  override def getChecked(namespaceName: String, resourceName: String)(
      implicit client: KubernetesClient[IO]
  ): IO[CustomResourceDefinition] =
    getChecked(resourceName)

  def getChecked(resourceName: String)(implicit client: KubernetesClient[IO]): IO[CustomResourceDefinition] =
    for {
      crdName  <- IO.pure(crdName(resourceName))
      resource <- client.customResourceDefinitions.get(crdName)
      _ = assertEquals(resource.metadata.flatMap(_.name), Some(crdName))
    } yield resource

  def sampleResource(resourceName: String, labels: Map[String, String]): CustomResourceDefinition =
    CustomResourceDefinitionsApiTest.crd(resourceName, labels)

  def modifyResource(resource: CustomResourceDefinition): CustomResourceDefinition =
    resource.copy(spec = resource.spec.copy(versions = Seq(versions.copy(served = false))))
  def checkUpdated(updatedResource: CustomResourceDefinition): Unit =
    assertEquals(updatedResource.spec.versions.headOption, versions.copy(served = false).some)

  override def afterAll(): Unit = {
    super.afterAll()
    val status = kubernetesClient
      .use(_.customResourceDefinitions.deleteAll(crdLabel))
      .unsafeRunSync()
    assertEquals(status, Status.Ok)
    ()
  }

  override def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]) =
    client.customResourceDefinitions

  def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.customResourceDefinitions

  override def watchApi(
      namespaceName: String
  )(implicit client: KubernetesClient[IO]): Watchable[IO, CustomResourceDefinition] =
    client.customResourceDefinitions
}
