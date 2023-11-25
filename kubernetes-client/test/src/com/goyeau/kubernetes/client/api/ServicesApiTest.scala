package com.goyeau.kubernetes.client.api

import cats.effect.*
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.MinikubeClientProvider
import com.goyeau.kubernetes.client.operation.*
import io.k8s.api.core.v1.*
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.typelevel.log4cats.Logger
import com.goyeau.kubernetes.client.TestPlatformSpecific

class ServicesApiTest
    extends MinikubeClientProvider
    with CreatableTests[Service]
    with GettableTests[Service]
    with ListableTests[Service, ServiceList]
    with ReplaceableTests[Service]
    with DeletableTests[Service, ServiceList]
    with WatchableTests[Service]
     {

  implicit override lazy val logger: Logger[IO] = TestPlatformSpecific.getLogger
  override lazy val resourceName: String        = classOf[Service].getSimpleName

  override def api(implicit client: KubernetesClient[IO]): ServicesApi[IO] = client.services
  override def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]): NamespacedServicesApi[IO] =
    client.services.namespace(namespaceName)

  override def sampleResource(resourceName: String, labels: Map[String, String]): Service = Service(
    metadata = Option(ObjectMeta(name = Option(resourceName), labels = Option(labels))),
    spec = Option(ServiceSpec(ports = Option(Seq(ServicePort(2000)))))
  )

  private val labels = Option(Map("test" -> "updated-label"))
  override def modifyResource(resource: Service): Service =
    resource.copy(metadata = resource.metadata.map(_.copy(labels = labels)))

  override def checkUpdated(updatedResource: Service): Unit =
    assertEquals(updatedResource.metadata.flatMap(_.labels), labels)

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.services.namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, Service] =
    client.services.namespace(namespaceName)
}
