package com.goyeau.kubernetes.client.api

import cats.Applicative
import cats.effect.*
import cats.implicits.*
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.api.CustomResourceDefinitionsApiTest.*
import com.goyeau.kubernetes.client.operation.*
import io.k8s.apiextensionsapiserver.pkg.apis.apiextensions.v1.*
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import munit.FunSuite
import munit.Assertions.*
import org.http4s.Status
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import fs2.io.file.Files

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

  implicit override lazy val F: Async[IO]       = IO.asyncForIO
  implicit override lazy val G: Files[IO]       = Files.forIO
  implicit override lazy val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
  override lazy val resourceName: String        = classOf[CustomResourceDefinition].getSimpleName
  override val resourceIsNamespaced             = false
  override val watchIsNamespaced: Boolean       = resourceIsNamespaced

  override def api(implicit client: KubernetesClient[IO]): CustomResourceDefinitionsApi[IO] =
    client.customResourceDefinitions
  override def delete(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[IO]): IO[Status] =
    namespacedApi(namespaceName).delete(crdName(resourceName))
  override def deleteResource(namespaceName: String, resourceName: String)(implicit
      client: KubernetesClient[IO]
  ): IO[Status] =
    namespacedApi(namespaceName).delete(crdName(resourceName))
  override def deleteTerminated(namespaceName: String, resourceName: String)(implicit
      client: KubernetesClient[IO]
  ): IO[Status] =
    namespacedApi(namespaceName).deleteTerminated(crdName(resourceName))

  override def listContains(namespaceName: String, resourceNames: Set[String], labels: Map[String, String])(implicit
      client: KubernetesClient[IO]
  ): IO[CustomResourceDefinitionList] = CustomResourceDefinitionsApiTest.listContains(resourceNames)

  override def getChecked(namespaceName: String, resourceName: String)(implicit
      client: KubernetesClient[IO]
  ): IO[CustomResourceDefinition] =
    CustomResourceDefinitionsApiTest.getChecked(resourceName)

  def sampleResource(resourceName: String, labels: Map[String, String]): CustomResourceDefinition =
    CustomResourceDefinitionsApiTest.crd(resourceName, labels ++ CustomResourceDefinitionsApiTest.crdLabel)

  def modifyResource(resource: CustomResourceDefinition): CustomResourceDefinition =
    resource.copy(spec = resource.spec.copy(versions = Seq(versions.copy(served = false))))

  def checkUpdated(updatedResource: CustomResourceDefinition): Unit =
    assertEquals(updatedResource.spec.versions.headOption, versions.copy(served = false).some)

  override def afterAll(): Unit = {
    usingMinikube { client =>
      for {
        status <- client.customResourceDefinitions.deleteAll(crdLabel)
        _ = assertEquals(status, Status.Ok, status.sanitizedReason)
        _ <- logger.info(s"All CRD with label '$crdLabel' are deleted.")
      } yield ()
    }
    super.afterAll()
  }

  override def namespacedApi(namespaceName: String)(implicit
      client: KubernetesClient[IO]
  ): CustomResourceDefinitionsApi[IO] =
    client.customResourceDefinitions

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.customResourceDefinitions

  override def watchApi(
      namespaceName: String
  )(implicit client: KubernetesClient[IO]): Watchable[IO, CustomResourceDefinition] =
    client.customResourceDefinitions
}

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
  val group    = "kubernetes.client.goyeau.com"

  def plural(resourceName: String): String = s"${resourceName.toLowerCase}s"

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
      labels = labels.some
    ).some
  )

  def createChecked[F[_]: Async](
      resourceName: String,
      labels: Map[String, String]
  )(implicit client: KubernetesClient[F]): F[CustomResourceDefinition] =
    for {
      status <- client.customResourceDefinitions.create(crd(resourceName, labels))
      _ = assertEquals(status, Status.Created, status.sanitizedReason)
      crd <- getChecked(resourceName)
      _   <- Sync[F].delay(println(s"CRD '$resourceName' created, labels: $labels"))
    } yield crd

  def getChecked[F[_]: Async](
      resourceName: String
  )(implicit client: KubernetesClient[F]): F[CustomResourceDefinition] =
    for {
      crdName  <- Applicative[F].pure(crdName(resourceName))
      resource <- client.customResourceDefinitions.get(crdName)
      _ = assertEquals(resource.metadata.flatMap(_.name), Some(crdName))
    } yield resource

  def listContains(resourceNames: Set[String])(implicit
      client: KubernetesClient[IO]
  ): IO[CustomResourceDefinitionList] =
    for {
      resourceList <- client.customResourceDefinitions.list()
      _ = assert(resourceNames.map(crdName).subsetOf(resourceList.items.flatMap(_.metadata.flatMap(_.name)).toSet))
    } yield resourceList

  def listNotContains(resourceNames: Set[String], labels: Map[String, String])(implicit
      client: KubernetesClient[IO]
  ): IO[CustomResourceDefinitionList] =
    for {
      resourceList <- client.customResourceDefinitions.list(labels)
      _ = assert(resourceList.items.flatMap(_.metadata.flatMap(_.name)).forall(!resourceNames.map(crdName).contains(_)))
    } yield resourceList
}
