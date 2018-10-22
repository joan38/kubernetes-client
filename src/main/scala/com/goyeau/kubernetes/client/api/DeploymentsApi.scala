package com.goyeau.kubernetes.client.api

import cats.effect.{Sync, Timer}
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import io.circe._
import io.k8s.api.apps.v1.{Deployment, DeploymentList}
import org.http4s.client.Client
import org.http4s.Uri.uri

private[client] case class DeploymentsApi[F[_]](httpClient: Client[F], config: KubeConfig)(
  implicit
  val F: Sync[F],
  timer: Timer[F],
  val listDecoder: Decoder[DeploymentList],
  encoder: Encoder[Deployment],
  decoder: Decoder[Deployment]
) extends Listable[F, DeploymentList] {
  val resourceUri = uri("/apis") / "apps" / "v1" / "deployments"

  def namespace(namespace: String) = NamespacedDeploymentsApi(httpClient, config, namespace)
}

private[client] case class NamespacedDeploymentsApi[F[_]](
  httpClient: Client[F],
  config: KubeConfig,
  namespace: String
)(
  implicit
  val F: Sync[F],
  val timer: Timer[F],
  val resourceEncoder: Encoder[Deployment],
  val resourceDecoder: Decoder[Deployment],
  val listDecoder: Decoder[DeploymentList]
) extends Creatable[F, Deployment]
    with Replaceable[F, Deployment]
    with Gettable[F, Deployment]
    with Listable[F, DeploymentList]
    with Deletable[F]
    with DeletableTerminated[F]
    with GroupDeletable[F] {
  val resourceUri = uri("/apis") / "apps" / "v1" / "namespaces" / namespace / "deployments"
}
