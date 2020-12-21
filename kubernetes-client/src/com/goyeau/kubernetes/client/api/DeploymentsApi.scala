package com.goyeau.kubernetes.client.api

import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import io.circe._
import io.k8s.api.apps.v1.{Deployment, DeploymentList}
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.implicits._

private[client] class DeploymentsApi[F[_]](val httpClient: Client[F], val config: KubeConfig)(implicit
    val F: Sync[F],
    val listDecoder: Decoder[DeploymentList],
    encoder: Encoder[Deployment],
    decoder: Decoder[Deployment]
) extends Listable[F, DeploymentList] {
  val resourceUri: Uri = uri"/apis" / "apps" / "v1" / "deployments"

  def namespace(namespace: String): NamespacedDeploymentsApi[F] =
    new NamespacedDeploymentsApi(httpClient, config, namespace)
}

private[client] class NamespacedDeploymentsApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig,
    namespace: String
)(implicit
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
    with Watchable[F, Deployment] {
  val resourceUri: Uri = uri"/apis" / "apps" / "v1" / "namespaces" / namespace / "deployments"
}
