package com.goyeau.kubernetes.client.api

import cats.effect._
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.operation._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.k8s.api.networking.v1beta1.{Ingress, IngressList, IngressRule, IngressSpec}
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import munit.FunSuite

class IngressesApiTest
    extends FunSuite
    with CreatableTests[IO, Ingress]
    with GettableTests[IO, Ingress]
    with ListableTests[IO, Ingress, IngressList]
    with ReplaceableTests[IO, Ingress]
    with DeletableTests[IO, Ingress, IngressList]
    with WatchableTests[IO, Ingress]
    with ContextProvider {

  implicit lazy val F: ConcurrentEffect[IO] = IO.ioConcurrentEffect
  implicit lazy val logger: Logger[IO]      = Slf4jLogger.getLogger[IO]
  lazy val resourceName                     = classOf[Ingress].getSimpleName

  override def api(implicit client: KubernetesClient[IO]) = client.ingresses
  override def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]) =
    client.ingresses.namespace(namespaceName)

  override def sampleResource(resourceName: String, labels: Map[String, String]) =
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

  override def modifyResource(resource: Ingress) =
    resource.copy(
      metadata = Option(ObjectMeta(name = resource.metadata.flatMap(_.name))),
      spec = updatedHost
    )
  override def checkUpdated(updatedResource: Ingress) =
    assertEquals(updatedResource.spec, updatedHost)

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.ingresses.namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, Ingress] =
    client.ingresses.namespace(namespaceName)
}
