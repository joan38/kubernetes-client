package com.goyeau.kubernetes.client.api

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation.*
import io.circe.*
import io.k8s.api.core.v1.{ConfigMap, ConfigMapList}
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.implicits.*

private[client] class ConfigMapsApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val authorization: Option[F[Authorization]]
)(implicit
    val F: Async[F],
    val listDecoder: Decoder[ConfigMapList],
    val resourceDecoder: Decoder[ConfigMap],
    encoder: Encoder[ConfigMap]
) extends Listable[F, ConfigMapList]
    with Watchable[F, ConfigMap] {
  val resourceUri: Uri = uri"/api" / "v1" / "configmaps"

  def namespace(namespace: String): NamespacedConfigMapsApi[F] =
    new NamespacedConfigMapsApi(httpClient, config, authorization, namespace)
}

private[client] class NamespacedConfigMapsApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val authorization: Option[F[Authorization]],
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
