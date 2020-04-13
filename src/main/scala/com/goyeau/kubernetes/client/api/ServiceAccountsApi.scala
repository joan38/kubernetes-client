package com.goyeau.kubernetes.client.api

import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import io.circe._
import io.k8s.api.core.v1.{ServiceAccount, ServiceAccountList}
import org.http4s.client.Client
import org.http4s.implicits._

private[client] case class ServiceAccountsApi[F[_]](httpClient: Client[F], config: KubeConfig)(
    implicit
    val F: Sync[F],
    val listDecoder: Decoder[ServiceAccountList],
    encoder: Encoder[ServiceAccount],
    decoder: Decoder[ServiceAccount]
) extends Listable[F, ServiceAccountList] {
  val resourceUri = uri"/api" / "v1" / "serviceaccounts"

  def namespace(namespace: String) = NamespacedServiceAccountsApi(httpClient, config, namespace)
}

private[client] case class NamespacedServiceAccountsApi[F[_]](
    httpClient: Client[F],
    config: KubeConfig,
    namespace: String
)(
    implicit
    val F: Sync[F],
    val resourceEncoder: Encoder[ServiceAccount],
    val resourceDecoder: Decoder[ServiceAccount],
    val listDecoder: Decoder[ServiceAccountList]
) extends Creatable[F, ServiceAccount]
    with Replaceable[F, ServiceAccount]
    with Gettable[F, ServiceAccount]
    with Listable[F, ServiceAccountList]
    with Deletable[F]
    with GroupDeletable[F] {
  val resourceUri = uri"/api" / "v1" / "namespaces" / namespace / "serviceaccounts"
}
