package com.goyeau.kubernetes.client.api

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import com.goyeau.kubernetes.client.util.cache.TokenCache
import io.circe._
import io.k8s.api.apps.v1.{ReplicaSet, ReplicaSetList}
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.implicits._

private[client] class ReplicaSetsApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val cachedExecToken: Option[TokenCache[F]]
)(implicit
    val F: Async[F],
    val listDecoder: Decoder[ReplicaSetList],
    val resourceDecoder: Decoder[ReplicaSet],
    encoder: Encoder[ReplicaSet]
) extends Listable[F, ReplicaSetList]
    with Watchable[F, ReplicaSet] {
  val resourceUri: Uri = uri"/apis" / "apps" / "v1" / "replicasets"

  def namespace(namespace: String): NamespacedReplicaSetsApi[F] =
    new NamespacedReplicaSetsApi(httpClient, config, cachedExecToken, namespace)
}

private[client] class NamespacedReplicaSetsApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val cachedExecToken: Option[TokenCache[F]],
    namespace: String
)(implicit
    val F: Async[F],
    val resourceEncoder: Encoder[ReplicaSet],
    val resourceDecoder: Decoder[ReplicaSet],
    val listDecoder: Decoder[ReplicaSetList]
) extends Creatable[F, ReplicaSet]
    with Replaceable[F, ReplicaSet]
    with Gettable[F, ReplicaSet]
    with Listable[F, ReplicaSetList]
    with Deletable[F]
    with DeletableTerminated[F]
    with GroupDeletable[F]
    with Watchable[F, ReplicaSet] {
  val resourceUri = uri"/apis" / "apps" / "v1" / "namespaces" / namespace / "replicasets"
}
