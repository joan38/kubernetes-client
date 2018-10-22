package com.goyeau.kubernetes.client.api

import cats.effect.{Sync, Timer}
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import io.circe._
import io.k8s.api.core.v1.{ConfigMap, ConfigMapList}
import org.http4s.client.Client
import org.http4s.Uri.uri

private[client] case class ConfigMapsApi[F[_]](httpClient: Client[F], config: KubeConfig)(
  implicit
  val F: Sync[F],
  timer: Timer[F],
  val listDecoder: Decoder[ConfigMapList],
  encoder: Encoder[ConfigMap],
  decoder: Decoder[ConfigMap]
) extends Listable[F, ConfigMapList] {
  val resourceUri = uri("/api") / "v1" / "configmaps"

  def namespace(namespace: String) = NamespacedConfigMapsApi(httpClient, config, namespace)
}

private[client] case class NamespacedConfigMapsApi[F[_]](
  httpClient: Client[F],
  config: KubeConfig,
  namespace: String
)(
  implicit
  val F: Sync[F],
  val timer: Timer[F],
  val resourceEncoder: Encoder[ConfigMap],
  val resourceDecoder: Decoder[ConfigMap],
  val listDecoder: Decoder[ConfigMapList]
) extends Creatable[F, ConfigMap]
    with Replaceable[F, ConfigMap]
    with Gettable[F, ConfigMap]
    with Listable[F, ConfigMapList]
    with Deletable[F]
    with GroupDeletable[F] {
  val resourceUri = uri("/api") / "v1" / "namespaces" / namespace / "configmaps"
}
