package com.goyeau.kubernetes.client.api

import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import io.circe._
import io.k8s.api.autoscaling.v1.{HorizontalPodAutoscaler, HorizontalPodAutoscalerList}
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.implicits._

private[client] case class HorizontalPodAutoscalersApi[F[_]](httpClient: Client[F], config: KubeConfig)(
    implicit
    val F: Sync[F],
    val listDecoder: Decoder[HorizontalPodAutoscalerList],
    encoder: Encoder[HorizontalPodAutoscaler],
    decoder: Decoder[HorizontalPodAutoscaler]
) extends Listable[F, HorizontalPodAutoscalerList] {
  val resourceUri: Uri = uri"/apis" / "autoscaling" / "v1" / "horizontalpodautoscalers"

  def namespace(namespace: String): NamespacedHorizontalPodAutoscalersApi[F] =
    NamespacedHorizontalPodAutoscalersApi(httpClient, config, namespace)
}

private[client] case class NamespacedHorizontalPodAutoscalersApi[F[_]](
    httpClient: Client[F],
    config: KubeConfig,
    namespace: String
)(
    implicit
    val F: Sync[F],
    val resourceEncoder: Encoder[HorizontalPodAutoscaler],
    val resourceDecoder: Decoder[HorizontalPodAutoscaler],
    val listDecoder: Decoder[HorizontalPodAutoscalerList]
) extends Creatable[F, HorizontalPodAutoscaler]
    with Replaceable[F, HorizontalPodAutoscaler]
    with Gettable[F, HorizontalPodAutoscaler]
    with Listable[F, HorizontalPodAutoscalerList]
    with Deletable[F]
    with GroupDeletable[F]
    with Watchable[F, HorizontalPodAutoscaler] {
  val resourceUri: Uri = uri"/apis" / "autoscaling" / "v1" / "namespaces" / namespace / "horizontalpodautoscalers"
}
