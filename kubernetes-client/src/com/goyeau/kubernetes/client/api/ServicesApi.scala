package com.goyeau.kubernetes.client.api

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation.*
import io.circe.*
import io.k8s.api.core.v1.{Service, ServiceList}
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.implicits.*

private[client] class ServicesApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val authorization: Option[F[Authorization]]
)(implicit
    val F: Async[F],
    val listDecoder: Decoder[ServiceList],
    val resourceDecoder: Decoder[Service],
    encoder: Encoder[Service]
) extends Listable[F, ServiceList]
    with Watchable[F, Service] {
  val resourceUri: Uri = uri"/api" / "v1" / "services"

  def namespace(namespace: String): NamespacedServicesApi[F] =
    new NamespacedServicesApi(httpClient, config, authorization, namespace)
}

private[client] class NamespacedServicesApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val authorization: Option[F[Authorization]],
    namespace: String
)(implicit
    val F: Async[F],
    val resourceEncoder: Encoder[Service],
    val resourceDecoder: Decoder[Service],
    val listDecoder: Decoder[ServiceList]
) extends Creatable[F, Service]
    with Replaceable[F, Service]
    with Gettable[F, Service]
    with Listable[F, ServiceList]
    with Proxy[F]
    with Deletable[F]
    with GroupDeletable[F]
    with Watchable[F, Service] {
  val resourceUri: Uri = uri"/api" / "v1" / "namespaces" / namespace / "services"
}
