package com.goyeau.kubernetes.client.api

import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import io.circe._
import io.k8s.api.core.v1.{ServiceAccount, ServiceAccountList}
import org.http4s.client.Client
import org.http4s.implicits._

private[client] case class ServiceAccountsApi[F[_]](
    httpClient: Client[F],
    config: KubeConfig,
    labels: Map[String, String] = Map.empty
)(
    implicit
    val F: Sync[F],
    val listDecoder: Decoder[ServiceAccountList],
    encoder: Encoder[ServiceAccount],
    decoder: Decoder[ServiceAccount]
) extends Listable[F, ServiceAccountList]
    with LabelSelector[ServiceAccountsApi[F]] {
  val resourceUri = uri"/api" / "v1" / "serviceaccounts"

  def namespace(namespace: String) = NamespacedServiceAccountsApi(httpClient, config, namespace)

  override def withLabels(labels: Map[String, String]): ServiceAccountsApi[F] =
    ServiceAccountsApi(httpClient, config, labels)
}

private[client] case class NamespacedServiceAccountsApi[F[_]](
    httpClient: Client[F],
    config: KubeConfig,
    namespace: String,
    labels: Map[String, String] = Map.empty
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
    with GroupDeletable[F]
    with Watchable[F, ServiceAccount]
    with LabelSelector[NamespacedServiceAccountsApi[F]] {
  val resourceUri = uri"/api" / "v1" / "namespaces" / namespace / "serviceaccounts"

  override def withLabels(labels: Map[String, String]): NamespacedServiceAccountsApi[F] =
    NamespacedServiceAccountsApi(httpClient, config, namespace, labels)
}
