package com.goyeau.kubernetes.client.api

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation.*
import io.circe.*
import io.k8s.api.coordination.v1.*
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.implicits.*

private[client] class LeasesApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val authorization: Option[F[Authorization]]
)(implicit
    val F: Async[F],
    val listDecoder: Decoder[LeaseList],
    val resourceDecoder: Decoder[Lease],
    encoder: Encoder[Lease]
) extends Listable[F, LeaseList]
    with Watchable[F, Lease] {

  val resourceUri: Uri = uri"/apis" / "coordination.k8s.io" / "v1" / "leases"

  def namespace(namespace: String): NamespacedLeasesApi[F] =
    new NamespacedLeasesApi(httpClient, config, authorization, namespace)

}

private[client] class NamespacedLeasesApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val authorization: Option[F[Authorization]],
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
