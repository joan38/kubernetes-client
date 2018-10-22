package com.goyeau.kubernetes.client.api

import cats.effect._
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.operation._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.k8s.api.autoscaling.v1.{
  CrossVersionObjectReference,
  HorizontalPodAutoscaler,
  HorizontalPodAutoscalerList,
  HorizontalPodAutoscalerSpec
}
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.scalatest.{FlatSpec, Matchers, OptionValues}

import scala.concurrent.ExecutionContext

class HorizontalPodAutoscalersApiTest
    extends FlatSpec
    with Matchers
    with OptionValues
    with CreatableTests[IO, HorizontalPodAutoscaler]
    with GettableTests[IO, HorizontalPodAutoscaler]
    with ListableTests[IO, HorizontalPodAutoscaler, HorizontalPodAutoscalerList]
    with ReplaceableTests[IO, HorizontalPodAutoscaler]
    with DeletableTests[IO, HorizontalPodAutoscaler, HorizontalPodAutoscalerList] {

  implicit lazy val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  implicit lazy val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit lazy val F: ConcurrentEffect[IO] = IO.ioConcurrentEffect
  implicit lazy val logger: Logger[IO] = Slf4jLogger.unsafeCreate[IO]
  lazy val resourceName = classOf[HorizontalPodAutoscaler].getSimpleName

  override def api(implicit client: KubernetesClient[IO]) = client.horizontalPodAutoscalers
  override def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]) =
    client.horizontalPodAutoscalers.namespace(namespaceName)

  override def sampleResource(resourceName: String) = HorizontalPodAutoscaler(
    metadata = Option(ObjectMeta(name = Option(resourceName))),
    spec =
      Option(HorizontalPodAutoscalerSpec(scaleTargetRef = CrossVersionObjectReference("kind", "name"), maxReplicas = 2))
  )
  val maxReplicas = 2
  override def modifyResource(resource: HorizontalPodAutoscaler) =
    resource.copy(
      metadata = Option(ObjectMeta(name = resource.metadata.flatMap(_.name))),
      spec = resource.spec.map(_.copy(maxReplicas = maxReplicas))
    )
  override def checkUpdated(updatedResource: HorizontalPodAutoscaler) =
    updatedResource.spec.get.maxReplicas shouldBe maxReplicas
}
