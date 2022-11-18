package com.goyeau.kubernetes.client.api

import cats.effect.*
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.operation.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.k8s.api.core.v1.{ConfigMap, ConfigMapList}
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import munit.FunSuite

class ConfigMapsApiTest
    extends FunSuite
    with CreatableTests[IO, ConfigMap]
    with GettableTests[IO, ConfigMap]
    with ListableTests[IO, ConfigMap, ConfigMapList]
    with ReplaceableTests[IO, ConfigMap]
    with DeletableTests[IO, ConfigMap, ConfigMapList]
    with WatchableTests[IO, ConfigMap]
    with ContextProvider {

  implicit override lazy val F: Async[IO]       = IO.asyncForIO
  implicit override lazy val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
  override lazy val resourceName: String        = classOf[ConfigMap].getSimpleName

  override def api(implicit client: KubernetesClient[IO]): ConfigMapsApi[IO] = client.configMaps
  override def namespacedApi(namespaceName: String)(implicit
      client: KubernetesClient[IO]
  ): NamespacedConfigMapsApi[IO] =
    client.configMaps.namespace(namespaceName)

  override def sampleResource(resourceName: String, labels: Map[String, String]): ConfigMap = ConfigMap(
    metadata = Option(ObjectMeta(name = Option(resourceName), labels = Option(labels))),
    data = Option(Map("test" -> "data"))
  )

  private val data = Option(Map("test" -> "updated-data"))
  override def modifyResource(resource: ConfigMap): ConfigMap =
    resource.copy(metadata = Option(ObjectMeta(name = resource.metadata.flatMap(_.name))), data = data)

  override def checkUpdated(updatedResource: ConfigMap): Unit = assertEquals(updatedResource.data, data)

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.configMaps.namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, ConfigMap] =
    client.configMaps.namespace(namespaceName)
}
