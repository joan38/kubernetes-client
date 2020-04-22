package com.goyeau.kubernetes.client.api

import cats.effect._
import cats.syntax.option._
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.operation._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.k8s.apiextensionsapiserver.pkg.apis.apiextensions.v1._
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s.Status
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Assertion, OptionValues}

class CustomResourceDefinitionsApiTest
    extends AnyFlatSpec
    with Matchers
    with OptionValues
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
  lazy val group                            = "kubernetes-client.goyeau.com"
  lazy val crdLabel                         = Map("test" -> "kubernetes-client")
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

  def listNotContains(resourceNames: Seq[String], labels: Map[String, String])(
      implicit client: KubernetesClient[IO]
  ): IO[CustomResourceDefinitionList] =
    for {
      resourceList <- client.customResourceDefinitions.withLabels(labels).list
      _ = (resourceList.items.map(_.metadata.value.name.value) should contain).noElementsOf(resourceNames.map(crdName))
    } yield resourceList

  override def listContains(namespaceName: String, resourceNames: Seq[String], labels: Map[String, String])(
      implicit client: KubernetesClient[IO]
  ): IO[CustomResourceDefinitionList] = listContains(resourceNames)

  def listContains(resourceNames: Seq[String])(
      implicit client: KubernetesClient[IO]
  ): IO[CustomResourceDefinitionList] =
    for {
      resourceList <- client.customResourceDefinitions.list
      _ = (resourceList.items.map(_.metadata.value.name.value) should contain).allElementsOf(resourceNames.map(crdName))
    } yield resourceList

  override def getChecked(namespaceName: String, resourceName: String)(
      implicit client: KubernetesClient[IO]
  ): IO[CustomResourceDefinition] =
    getChecked(resourceName)

  def getChecked(resourceName: String)(implicit client: KubernetesClient[IO]): IO[CustomResourceDefinition] =
    for {
      crdName  <- IO.pure(crdName(resourceName))
      resource <- client.customResourceDefinitions.get(crdName)
      _ = resource.metadata.value.name.value shouldBe crdName
    } yield resource

  def plural(resourceName: String): String  = s"${resourceName}s"
  def crdName(resourceName: String): String = s"${plural(resourceName)}.$group"

  def sampleResource(resourceName: String, labels: Map[String, String]): CustomResourceDefinition =
    CustomResourceDefinition(
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
            )
          ).some
        ).some
      ).some
    )

  def modifyResource(resource: CustomResourceDefinition): CustomResourceDefinition =
    resource.copy(
      spec = resource.spec.copy(versions = Seq(versions.copy(served = false)))
    )
  def checkUpdated(updatedResource: CustomResourceDefinition): Assertion =
    updatedResource.spec.versions.headOption shouldBe versions.copy(served = false).some

  override def afterAll(): Unit = {
    super.afterAll()
    val status = kubernetesClient
      .use(implicit client => client.customResourceDefinitions.withLabels(crdLabel).delete)
      .unsafeRunSync()
    status shouldBe Status.Ok
    ()
  }

  override def namespacedApi(namespaceName: String, labels: Map[String, String] = Map.empty)(
      implicit client: KubernetesClient[IO]
  ) =
    client.customResourceDefinitions.withLabels(labels)

  def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.customResourceDefinitions

  override def watchApi(
      namespaceName: String
  )(implicit client: KubernetesClient[IO]): Watchable[IO, CustomResourceDefinition] =
    client.customResourceDefinitions
}
