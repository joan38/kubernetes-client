package com.goyeau.kubernetes.client.api

import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import io.circe._
import io.k8s.api.core.v1.{ConfigMap, ConfigMapList}
import org.http4s.client.Client
import org.http4s.implicits._

private[client] case class ConfigMapsApi[F[_]](
    httpClient: Client[F],
    config: KubeConfig,
    labels: Map[String, String] = Map.empty
)(
    implicit
    val F: Sync[F],
    val listDecoder: Decoder[ConfigMapList],
    encoder: Encoder[ConfigMap],
    decoder: Decoder[ConfigMap]
) extends Listable[F, ConfigMapList]
    with LabelSelector[ConfigMapsApi[F]] {
  val resourceUri = uri"/api" / "v1" / "configmaps"

  def namespace(namespace: String) = NamespacedConfigMapsApi(httpClient, config, namespace)

  override def withLabels(labels: Map[String, String]): ConfigMapsApi[F] =
    ConfigMapsApi(httpClient, config, labels)
}

private[client] case class NamespacedConfigMapsApi[F[_]](
    httpClient: Client[F],
    config: KubeConfig,
    namespace: String,
    labels: Map[String, String] = Map.empty
)(
    implicit
    val F: Sync[F],
    val resourceEncoder: Encoder[ConfigMap],
    val resourceDecoder: Decoder[ConfigMap],
    val listDecoder: Decoder[ConfigMapList]
) extends Creatable[F, ConfigMap]
    with Replaceable[F, ConfigMap]
    with Gettable[F, ConfigMap]
    with Listable[F, ConfigMapList]
    with Deletable[F]
    with GroupDeletable[F]
    with Watchable[F, ConfigMap]
    with LabelSelector[NamespacedConfigMapsApi[F]] {
  val resourceUri = uri"/api" / "v1" / "namespaces" / namespace / "configmaps"

  override def withLabels(labels: Map[String, String]): NamespacedConfigMapsApi[F] =
    NamespacedConfigMapsApi(httpClient, config, namespace, labels)
}
