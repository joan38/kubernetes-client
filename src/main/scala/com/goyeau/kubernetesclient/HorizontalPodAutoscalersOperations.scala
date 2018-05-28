package com.goyeau.kubernetesclient

import akka.actor.ActorSystem
import io.circe._
import io.k8s.api.autoscaling.v1.{HorizontalPodAutoscaler, HorizontalPodAutoscalerList}

private[kubernetesclient] case class HorizontalPodAutoscalersOperations(protected val config: KubeConfig)(
  implicit protected val system: ActorSystem,
  protected val listDecoder: Decoder[HorizontalPodAutoscalerList],
  encoder: Encoder[HorizontalPodAutoscaler],
  decoder: Decoder[HorizontalPodAutoscaler]
) extends Listable[HorizontalPodAutoscalerList] {
  protected val resourceUri = "apis/autoscaling/v1/horizontalpodautoscalers"

  def namespace(namespace: String) = NamespacedHorizontalPodAutoscalersOperations(config, namespace)
}

private[kubernetesclient] case class NamespacedHorizontalPodAutoscalersOperations(
  protected val config: KubeConfig,
  protected val namespace: String
)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[HorizontalPodAutoscaler],
  protected val resourceDecoder: Decoder[HorizontalPodAutoscaler],
  protected val listDecoder: Decoder[HorizontalPodAutoscalerList]
) extends Creatable[HorizontalPodAutoscaler]
    with Replaceable[HorizontalPodAutoscaler]
    with Gettable[HorizontalPodAutoscaler]
    with Listable[HorizontalPodAutoscalerList]
    with Deletable
    with GroupDeletable {
  protected val resourceUri = s"apis/autoscaling/v1/namespaces/$namespace/horizontalpodautoscalers"
}
