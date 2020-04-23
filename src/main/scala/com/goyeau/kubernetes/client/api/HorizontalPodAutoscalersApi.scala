package com.goyeau.kubernetes.client.api

import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import io.circe._
import io.k8s.api.autoscaling.v1.{HorizontalPodAutoscaler, HorizontalPodAutoscalerList}
import org.http4s.client.Client
import org.http4s.implicits._

private[client] case class HorizontalPodAutoscalersApi[F[_]](
    httpClient: Client[F],
    config: KubeConfig,
    labels: Map[String, String] = Map.empty
)(
    implicit
    val F: Sync[F],
    val listDecoder: Decoder[HorizontalPodAutoscalerList],
    encoder: Encoder[HorizontalPodAutoscaler],
    decoder: Decoder[HorizontalPodAutoscaler]
) extends Listable[F, HorizontalPodAutoscalerList]
    with Filterable[HorizontalPodAutoscalersApi[F]] {
  val resourceUri = uri"/apis" / "autoscaling" / "v1" / "horizontalpodautoscalers"

  def namespace(namespace: String) = NamespacedHorizontalPodAutoscalersApi(httpClient, config, namespace)

  override def withLabels(labels: Map[String, String]): HorizontalPodAutoscalersApi[F] =
    HorizontalPodAutoscalersApi(httpClient, config, labels)

}

private[client] case class NamespacedHorizontalPodAutoscalersApi[F[_]](
    httpClient: Client[F],
    config: KubeConfig,
    namespace: String,
    labels: Map[String, String] = Map.empty
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
    with Watchable[F, HorizontalPodAutoscaler]
    with Filterable[NamespacedHorizontalPodAutoscalersApi[F]] {
  val resourceUri = uri"/apis" / "autoscaling" / "v1" / "namespaces" / namespace / "horizontalpodautoscalers"

  override def withLabels(labels: Map[String, String]): NamespacedHorizontalPodAutoscalersApi[F] =
    NamespacedHorizontalPodAutoscalersApi(httpClient, config, namespace, labels)
}
