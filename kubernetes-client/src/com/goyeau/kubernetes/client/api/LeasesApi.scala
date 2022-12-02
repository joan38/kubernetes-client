package com.goyeau.kubernetes.client.api

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import com.goyeau.kubernetes.client.util.cache.TokenCache
import io.circe._
import io.k8s.api.coordination.v1._
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.implicits._

private[client] class LeasesApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val cachedExecToken: Option[TokenCache[F]]
)(implicit
    val F: Async[F],
    val listDecoder: Decoder[LeaseList],
    val resourceDecoder: Decoder[Lease],
    encoder: Encoder[Lease]
) extends Listable[F, LeaseList]
    with Watchable[F, Lease] {

  val resourceUri: Uri = uri"/apis" / "coordination.k8s.io" / "v1" / "leases"

  def namespace(namespace: String): NamespacedLeasesApi[F] =
    new NamespacedLeasesApi(httpClient, config, cachedExecToken, namespace)

}

private[client] class NamespacedLeasesApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val cachedExecToken: Option[TokenCache[F]],
    namespace: String
)(implicit
    val F: Async[F],
    val resourceEncoder: Encoder[Lease],
    val resourceDecoder: Decoder[Lease],
    val listDecoder: Decoder[LeaseList]
) extends Creatable[F, Lease]
    with Replaceable[F, Lease]
    with Gettable[F, Lease]
    with Listable[F, LeaseList]
    with Deletable[F]
    with DeletableTerminated[F]
    with GroupDeletable[F]
    with Watchable[F, Lease] {
  val resourceUri: Uri = uri"/apis" / "coordination.k8s.io" / "v1" / "namespaces" / namespace / "leases"
}
