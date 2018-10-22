package com.goyeau.kubernetes.client.api

import cats.effect.{Sync, Timer}
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import io.circe._
import io.k8s.api.apps.v1.{ReplicaSet, ReplicaSetList}
import org.http4s.client.Client
import org.http4s.Uri.uri

private[client] case class ReplicaSetsApi[F[_]](httpClient: Client[F], config: KubeConfig)(
  implicit
  val F: Sync[F],
  timer: Timer[F],
  val listDecoder: Decoder[ReplicaSetList],
  encoder: Encoder[ReplicaSet],
  decoder: Decoder[ReplicaSet]
) extends Listable[F, ReplicaSetList] {
  val resourceUri = uri("/apis") / "apps" / "v1" / "replicasets"

  def namespace(namespace: String) = NamespacedReplicaSetsApi(httpClient, config, namespace)
}

private[client] case class NamespacedReplicaSetsApi[F[_]](
  httpClient: Client[F],
  config: KubeConfig,
  namespace: String
)(
  implicit
  val F: Sync[F],
  val timer: Timer[F],
  val resourceEncoder: Encoder[ReplicaSet],
  val resourceDecoder: Decoder[ReplicaSet],
  val listDecoder: Decoder[ReplicaSetList]
) extends Creatable[F, ReplicaSet]
    with Replaceable[F, ReplicaSet]
    with Gettable[F, ReplicaSet]
    with Listable[F, ReplicaSetList]
    with Deletable[F]
    with DeletableTerminated[F]
    with GroupDeletable[F] {
  val resourceUri = uri("/apis") / "apps" / "v1" / "namespaces" / namespace / "replicasets"
}
