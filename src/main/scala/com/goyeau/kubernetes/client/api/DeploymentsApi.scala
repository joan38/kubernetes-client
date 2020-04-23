package com.goyeau.kubernetes.client.api

import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import io.circe._
import io.k8s.api.apps.v1.{Deployment, DeploymentList}
import org.http4s.client.Client
import org.http4s.implicits._

private[client] case class DeploymentsApi[F[_]](
    httpClient: Client[F],
    config: KubeConfig,
    labels: Map[String, String] = Map.empty
)(
    implicit
    val F: Sync[F],
    val listDecoder: Decoder[DeploymentList],
    encoder: Encoder[Deployment],
    decoder: Decoder[Deployment]
) extends Listable[F, DeploymentList]
    with Filterable[DeploymentsApi[F]] {
  val resourceUri = uri"/apis" / "apps" / "v1" / "deployments"

  def namespace(namespace: String) = NamespacedDeploymentsApi(httpClient, config, namespace)

  override def withLabels(labels: Map[String, String]): DeploymentsApi[F] =
    DeploymentsApi(httpClient, config, labels)
}

private[client] case class NamespacedDeploymentsApi[F[_]](
    httpClient: Client[F],
    config: KubeConfig,
    namespace: String,
    labels: Map[String, String] = Map.empty
)(
    implicit
    val F: Sync[F],
    val resourceEncoder: Encoder[Deployment],
    val resourceDecoder: Decoder[Deployment],
    val listDecoder: Decoder[DeploymentList]
) extends Creatable[F, Deployment]
    with Replaceable[F, Deployment]
    with Gettable[F, Deployment]
    with Listable[F, DeploymentList]
    with Deletable[F]
    with DeletableTerminated[F]
    with GroupDeletable[F]
    with Watchable[F, Deployment]
    with Filterable[NamespacedDeploymentsApi[F]] {
  val resourceUri = uri"/apis" / "apps" / "v1" / "namespaces" / namespace / "deployments"

  override def withLabels(labels: Map[String, String]): NamespacedDeploymentsApi[F] =
    NamespacedDeploymentsApi(httpClient, config, namespace, labels)
}
