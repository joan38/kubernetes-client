package com.goyeau.kubernetes.client.api

import cats.effect.*
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.operation.*
import io.k8s.api.core.v1.*
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import munit.FunSuite
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class ServicesApiTest
    extends FunSuite
    with CreatableTests[IO, Service]
    with GettableTests[IO, Service]
    with ListableTests[IO, Service, ServiceList]
    with ReplaceableTests[IO, Service]
    with DeletableTests[IO, Service, ServiceList]
    with WatchableTests[IO, Service]
    with ContextProvider {

  implicit lazy val F: Async[IO]       = IO.asyncForIO
  implicit lazy val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
  lazy val resourceName                = classOf[Service].getSimpleName

  override def api(implicit client: KubernetesClient[IO]) = client.services
  override def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]) =
    client.services.namespace(namespaceName)

  override def sampleResource(resourceName: String, labels: Map[String, String]) = Service(
    metadata = Option(ObjectMeta(name = Option(resourceName), labels = Option(labels))),
    spec = Option(ServiceSpec(ports = Option(Seq(ServicePort(2000)))))
  )
  val labels = Option(Map("test" -> "updated-label"))
  override def modifyResource(resource: Service) =
    resource.copy(metadata = resource.metadata.map(_.copy(labels = labels)))
  override def checkUpdated(updatedResource: Service) = assertEquals(updatedResource.metadata.flatMap(_.labels), labels)

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.services.namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, Service] =
    client.services.namespace(namespaceName)
}
