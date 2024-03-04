package com.goyeau.kubernetes.client.api

import cats.effect.*
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.MinikubeClientProvider
import com.goyeau.kubernetes.client.operation.*
import org.typelevel.log4cats.Logger
import com.goyeau.kubernetes.client.TestPlatformSpecific
import io.k8s.api.networking.v1.{Ingress, IngressList, IngressRule, IngressSpec}
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta

class IngressesApiTest
    extends MinikubeClientProvider
    with CreatableTests[Ingress]
    with GettableTests[Ingress]
    with ListableTests[Ingress, IngressList]
    with ReplaceableTests[Ingress]
    with DeletableTests[Ingress, IngressList]
    with WatchableTests[Ingress]
     {

  implicit override lazy val logger: Logger[IO] = TestPlatformSpecific.getLogger
  override lazy val resourceName: String        = classOf[Ingress].getSimpleName

  override def api(implicit client: KubernetesClient[IO]): IngressessApi[IO] = client.ingresses
  override def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]): NamespacedIngressesApi[IO] =
    client.ingresses.namespace(namespaceName)

  override def sampleResource(resourceName: String, labels: Map[String, String]): Ingress =
    Ingress(
      metadata = Option(ObjectMeta(name = Option(resourceName), labels = Option(labels))),
      spec = Option(
        IngressSpec(
          rules = Some(Seq(IngressRule(Some("host"))))
        )
      )
    )

  private val updatedHost: Option[IngressSpec] = Option(
    IngressSpec(
      rules = Some(Seq(IngressRule(Some("host2"))))
    )
  )

  override def modifyResource(resource: Ingress): Ingress =
    resource.copy(
      metadata = Option(ObjectMeta(name = resource.metadata.flatMap(_.name))),
      spec = updatedHost
    )
  override def checkUpdated(updatedResource: Ingress): Unit =
    assertEquals(updatedResource.spec, updatedHost)

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.ingresses.namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, Ingress] =
    client.ingresses.namespace(namespaceName)
}
