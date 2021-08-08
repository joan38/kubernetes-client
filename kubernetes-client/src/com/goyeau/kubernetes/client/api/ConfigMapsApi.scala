package com.goyeau.kubernetes.client.api

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import io.circe._
import io.k8s.api.core.v1.{ConfigMap, ConfigMapList}
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.implicits._

private[client] class ConfigMapsApi[F[_]](val httpClient: Client[F], val config: KubeConfig)(implicit
    val F: Async[F],
    val listDecoder: Decoder[ConfigMapList],
    encoder: Encoder[ConfigMap],
    decoder: Decoder[ConfigMap]
) extends Listable[F, ConfigMapList] {
  val resourceUri: Uri = uri"/api" / "v1" / "configmaps"

  def namespace(namespace: String): NamespacedConfigMapsApi[F] =
    new NamespacedConfigMapsApi(httpClient, config, namespace)
}

private[client] class NamespacedConfigMapsApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig,
    val namespace: String
)(implicit
    val F: Async[F],
    val resourceEncoder: Encoder[ConfigMap],
    val resourceDecoder: Decoder[ConfigMap],
    val listDecoder: Decoder[ConfigMapList]
) extends Creatable[F, ConfigMap]
    with Replaceable[F, ConfigMap]
    with Gettable[F, ConfigMap]
    with Listable[F, ConfigMapList]
    with Deletable[F]
    with GroupDeletable[F]
    with Watchable[F, ConfigMap] {
  val resourceUri: Uri = uri"/api" / "v1" / "namespaces" / namespace / "configmaps"
}
