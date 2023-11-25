package com.goyeau.kubernetes.client.api

import cats.effect.*
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.MinikubeClientProvider
import com.goyeau.kubernetes.client.operation.*
import org.typelevel.log4cats.Logger
import com.goyeau.kubernetes.client.TestPlatformSpecific
import io.k8s.api.autoscaling.v1.{
  CrossVersionObjectReference,
  HorizontalPodAutoscaler,
  HorizontalPodAutoscalerList,
  HorizontalPodAutoscalerSpec
}
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta

class HorizontalPodAutoscalersApiTest
    extends MinikubeClientProvider
    with CreatableTests[HorizontalPodAutoscaler]
    with GettableTests[HorizontalPodAutoscaler]
    with ListableTests[HorizontalPodAutoscaler, HorizontalPodAutoscalerList]
    with ReplaceableTests[HorizontalPodAutoscaler]
    with DeletableTests[HorizontalPodAutoscaler, HorizontalPodAutoscalerList]
    with WatchableTests[HorizontalPodAutoscaler]
     {

  implicit override lazy val logger: Logger[IO] = TestPlatformSpecific.getLogger
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

  val maxReplicas = 3
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
