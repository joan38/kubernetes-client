package com.goyeau.kubernetes.client.api

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import io.circe._
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.implicits._
import io.k8s.api.networking.v1beta1.{Ingress, IngressList}

private[client] class IngressessApi[F[_]](val httpClient: Client[F], val config: KubeConfig)(implicit
    val F: Async[F],
    val listDecoder: Decoder[IngressList],
    encoder: Encoder[Ingress],
    decoder: Decoder[Ingress]
) extends Listable[F, IngressList] {
  val resourceUri: Uri = uri"/apis" / "extensions" / "v1beta1" / "ingresses"

  def namespace(namespace: String): NamespacedIngressesApi[F] =
    new NamespacedIngressesApi(httpClient, config, namespace)
}

private[client] class NamespacedIngressesApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig,
    namespace: String
)(implicit
    val F: Async[F],
    val resourceEncoder: Encoder[Ingress],
    val resourceDecoder: Decoder[Ingress],
    val listDecoder: Decoder[IngressList]
) extends Creatable[F, Ingress]
    with Replaceable[F, Ingress]
    with Gettable[F, Ingress]
    with Listable[F, IngressList]
    with Deletable[F]
    with GroupDeletable[F]
    with Watchable[F, Ingress] {
  val resourceUri: Uri = uri"/apis" / "extensions" / "v1beta1" / "namespaces" / namespace / "ingresses"
}
