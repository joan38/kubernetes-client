package com.goyeau.kubernetes.client.api

import cats.effect._
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.operation._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.k8s.api.core.v1._
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers

class ServicesApiTest
    extends AnyFlatSpec
    with Matchers
    with OptionValues
    with CreatableTests[IO, Service]
    with GettableTests[IO, Service]
    with ListableTests[IO, Service, ServiceList]
    with ReplaceableTests[IO, Service]
    with DeletableTests[IO, Service, ServiceList]
    with WatchableTests[IO, Service]
    with ContextProvider {

  implicit lazy val F: ConcurrentEffect[IO] = IO.ioConcurrentEffect
  implicit lazy val logger: Logger[IO]      = Slf4jLogger.getLogger[IO]
  lazy val resourceName                     = classOf[Service].getSimpleName

  override def api(implicit client: KubernetesClient[IO]) = client.services
  override def namespacedApi(namespaceName: String, labels: Map[String, String])(
      implicit client: KubernetesClient[IO]
  ) =
    client.services.namespace(namespaceName).withLabels(labels)

  override def sampleResource(resourceName: String, labels: Map[String, String]) = Service(
    metadata = Option(ObjectMeta(name = Option(resourceName), labels = Option(labels))),
    spec = Option(ServiceSpec(ports = Option(Seq(ServicePort(2000)))))
  )
  val labels = Option(Map("test" -> "updated-label"))
  override def modifyResource(resource: Service) =
    resource.copy(metadata = resource.metadata.map(_.copy(labels = labels)))
  override def checkUpdated(updatedResource: Service) = updatedResource.metadata.value.labels shouldBe labels

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.services.namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, Service] =
    client.services.namespace(namespaceName)
}
