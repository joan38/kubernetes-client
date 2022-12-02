package com.goyeau.kubernetes.client.api

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import com.goyeau.kubernetes.client.util.CachedExecToken
import io.circe._
import io.k8s.api.autoscaling.v1.{HorizontalPodAutoscaler, HorizontalPodAutoscalerList}
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.implicits._

private[client] class HorizontalPodAutoscalersApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val cachedExecToken: Option[CachedExecToken[F]]
)(implicit
    val F: Async[F],
    val listDecoder: Decoder[HorizontalPodAutoscalerList],
    val resourceDecoder: Decoder[HorizontalPodAutoscaler],
    encoder: Encoder[HorizontalPodAutoscaler]
) extends Listable[F, HorizontalPodAutoscalerList]
    with Watchable[F, HorizontalPodAutoscaler] {
  val resourceUri: Uri = uri"/apis" / "autoscaling" / "v1" / "horizontalpodautoscalers"

  def namespace(namespace: String): NamespacedHorizontalPodAutoscalersApi[F] =
    new NamespacedHorizontalPodAutoscalersApi(httpClient, config, cachedExecToken, namespace)
}

private[client] class NamespacedHorizontalPodAutoscalersApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val cachedExecToken: Option[CachedExecToken[F]],
    namespace: String
)(implicit
    val F: Async[F],
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
