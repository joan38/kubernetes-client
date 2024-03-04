package com.goyeau.kubernetes.client.api

import cats.effect.*
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.MinikubeClientProvider
import com.goyeau.kubernetes.client.operation.*
import org.typelevel.log4cats.Logger
import com.goyeau.kubernetes.client.TestPlatformSpecific
import io.k8s.api.core.v1.{ConfigMap, ConfigMapList}
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta

class ConfigMapsApiTest
    extends MinikubeClientProvider
    with CreatableTests[ConfigMap]
    with GettableTests[ConfigMap]
    with ListableTests[ConfigMap, ConfigMapList]
    with ReplaceableTests[ConfigMap]
    with DeletableTests[ConfigMap, ConfigMapList]
    with WatchableTests[ConfigMap]
     {

  implicit override lazy val logger: Logger[IO] = TestPlatformSpecific.getLogger
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
