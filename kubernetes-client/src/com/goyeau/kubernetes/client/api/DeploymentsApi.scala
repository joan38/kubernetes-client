package com.goyeau.kubernetes.client.api

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation.*
import io.circe.*
import io.k8s.api.apps.v1.{Deployment, DeploymentList}
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.implicits.*

private[client] class DeploymentsApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val authorization: Option[F[Authorization]]
)(implicit
    val F: Async[F],
    val listDecoder: Decoder[DeploymentList],
    val resourceDecoder: Decoder[Deployment],
    encoder: Encoder[Deployment]
) extends Listable[F, DeploymentList]
    with Watchable[F, Deployment] {
  val resourceUri: Uri = uri"/apis" / "apps" / "v1" / "deployments"

  def namespace(namespace: String): NamespacedDeploymentsApi[F] =
    new NamespacedDeploymentsApi(httpClient, config, authorization, namespace)
}

private[client] class NamespacedDeploymentsApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val authorization: Option[F[Authorization]],
    namespace: String
)(implicit
    val F: Async[F],
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
