package com.goyeau.kubernetes.client.api

import cats.effect.*
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.operation.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.k8s.api.autoscaling.v1.{
  CrossVersionObjectReference,
  HorizontalPodAutoscaler,
  HorizontalPodAutoscalerList,
  HorizontalPodAutoscalerSpec
}
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import munit.FunSuite
import fs2.io.file.Files

class HorizontalPodAutoscalersApiTest
    extends FunSuite
    with CreatableTests[IO, HorizontalPodAutoscaler]
    with GettableTests[IO, HorizontalPodAutoscaler]
    with ListableTests[IO, HorizontalPodAutoscaler, HorizontalPodAutoscalerList]
    with ReplaceableTests[IO, HorizontalPodAutoscaler]
    with DeletableTests[IO, HorizontalPodAutoscaler, HorizontalPodAutoscalerList]
    with WatchableTests[IO, HorizontalPodAutoscaler]
    with ContextProvider {

  implicit override lazy val F: Async[IO]       = IO.asyncForIO
  implicit override lazy val G: Files[IO]       = Files.forIO
  implicit override lazy val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
  override lazy val resourceName: String        = classOf[HorizontalPodAutoscaler].getSimpleName

  override def api(implicit client: KubernetesClient[IO]): HorizontalPodAutoscalersApi[IO] =
    client.horizontalPodAutoscalers
  override def namespacedApi(namespaceName: String)(implicit
      client: KubernetesClient[IO]
  ): NamespacedHorizontalPodAutoscalersApi[IO] =
    client.horizontalPodAutoscalers.namespace(namespaceName)

  override def sampleResource(resourceName: String, labels: Map[String, String]): HorizontalPodAutoscaler =
    HorizontalPodAutoscaler(
      metadata = Option(ObjectMeta(name = Option(resourceName), labels = Option(labels))),
      spec = Option(
        HorizontalPodAutoscalerSpec(scaleTargetRef = CrossVersionObjectReference("kind", "name"), maxReplicas = 2)
      )
    )

  val maxReplicas                                                                         = 3
  override def modifyResource(resource: HorizontalPodAutoscaler): HorizontalPodAutoscaler =
    resource.copy(
      metadata = Option(ObjectMeta(name = resource.metadata.flatMap(_.name))),
      spec = resource.spec.map(_.copy(maxReplicas = maxReplicas))
    )

  override def checkUpdated(updatedResource: HorizontalPodAutoscaler): Unit =
    assertEquals(updatedResource.spec.get.maxReplicas, maxReplicas)

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.horizontalPodAutoscalers.namespace(namespaceName)

  override def watchApi(
      namespaceName: String
  )(implicit client: KubernetesClient[IO]): Watchable[IO, HorizontalPodAutoscaler] =
    client.horizontalPodAutoscalers.namespace(namespaceName)
}
