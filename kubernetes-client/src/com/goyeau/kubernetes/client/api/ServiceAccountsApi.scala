package com.goyeau.kubernetes.client.api

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation.*
import io.circe.*
import io.k8s.api.core.v1.{ServiceAccount, ServiceAccountList}
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.implicits.*

private[client] class ServiceAccountsApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val authorization: Option[F[Authorization]]
)(implicit
    val F: Async[F],
    val listDecoder: Decoder[ServiceAccountList],
    val resourceDecoder: Decoder[ServiceAccount],
    encoder: Encoder[ServiceAccount]
) extends Listable[F, ServiceAccountList]
    with Watchable[F, ServiceAccount] {
  val resourceUri: Uri = uri"/api" / "v1" / "serviceaccounts"

  def namespace(namespace: String): NamespacedServiceAccountsApi[F] =
    new NamespacedServiceAccountsApi(httpClient, config, authorization, namespace)
}

private[client] class NamespacedServiceAccountsApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val authorization: Option[F[Authorization]],
    namespace: String
)(implicit
    val F: Async[F],
    val resourceEncoder: Encoder[ServiceAccount],
    val resourceDecoder: Decoder[ServiceAccount],
    val listDecoder: Decoder[ServiceAccountList]
) extends Creatable[F, ServiceAccount]
    with Replaceable[F, ServiceAccount]
    with Gettable[F, ServiceAccount]
    with Listable[F, ServiceAccountList]
    with Deletable[F]
    with GroupDeletable[F]
    with Watchable[F, ServiceAccount] {
  val resourceUri: Uri = uri"/api" / "v1" / "namespaces" / namespace / "serviceaccounts"
}
