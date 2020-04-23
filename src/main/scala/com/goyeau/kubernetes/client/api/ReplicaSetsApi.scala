package com.goyeau.kubernetes.client.api

import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import io.circe._
import io.k8s.api.apps.v1.{ReplicaSet, ReplicaSetList}
import org.http4s.client.Client
import org.http4s.implicits._

private[client] case class ReplicaSetsApi[F[_]](
    httpClient: Client[F],
    config: KubeConfig,
    labels: Map[String, String] = Map.empty
)(
    implicit
    val F: Sync[F],
    val listDecoder: Decoder[ReplicaSetList],
    encoder: Encoder[ReplicaSet],
    decoder: Decoder[ReplicaSet]
) extends Listable[F, ReplicaSetList]
    with Filterable[ReplicaSetsApi[F]] {
  val resourceUri = uri"/apis" / "apps" / "v1" / "replicasets"

  def namespace(namespace: String) =
    NamespacedReplicaSetsApi(httpClient, config, namespace)

  override def withLabels(labels: Map[String, String]): ReplicaSetsApi[F] =
    ReplicaSetsApi(httpClient, config, labels)
}

private[client] case class NamespacedReplicaSetsApi[F[_]](
    httpClient: Client[F],
    config: KubeConfig,
    namespace: String,
    labels: Map[String, String] = Map.empty
)(
    implicit
    val F: Sync[F],
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
    with Watchable[F, ReplicaSet]
    with Filterable[NamespacedReplicaSetsApi[F]] {
  val resourceUri = uri"/apis" / "apps" / "v1" / "namespaces" / namespace / "replicasets"

  override def withLabels(labels: Map[String, String]): NamespacedReplicaSetsApi[F] =
    NamespacedReplicaSetsApi(httpClient, config, namespace, labels)
}
