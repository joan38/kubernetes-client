package com.goyeau.kubernetes.client.api

import cats.effect._
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.operation._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.k8s.api.core.v1.{ConfigMap, ConfigMapList}
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext

class ConfigMapsApiTest
    extends AnyFlatSpec
    with Matchers
    with OptionValues
    with CreatableTests[IO, ConfigMap]
    with GettableTests[IO, ConfigMap]
    with ListableTests[IO, ConfigMap, ConfigMapList]
    with ReplaceableTests[IO, ConfigMap]
    with DeletableTests[IO, ConfigMap, ConfigMapList] {

  implicit lazy val timer: Timer[IO]               = IO.timer(ExecutionContext.global)
  implicit lazy val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit lazy val F: ConcurrentEffect[IO]        = IO.ioConcurrentEffect
  implicit lazy val logger: Logger[IO]             = Slf4jLogger.getLogger[IO]
  lazy val resourceName                            = classOf[ConfigMap].getSimpleName

  override def api(implicit client: KubernetesClient[IO]) = client.configMaps
  override def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]) =
    client.configMaps.namespace(namespaceName)

  override def sampleResource(resourceName: String) = ConfigMap(
    metadata = Option(ObjectMeta(name = Option(resourceName))),
    data = Option(Map("test" -> "data"))
  )
  val data = Option(Map("test" -> "updated-data"))
  override def modifyResource(resource: ConfigMap) =
    resource.copy(metadata = Option(ObjectMeta(name = resource.metadata.flatMap(_.name))), data = data)
  override def checkUpdated(updatedResource: ConfigMap) = updatedResource.data shouldBe data
}
