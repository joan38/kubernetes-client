package com.goyeau.kubernetes.client.api

import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import io.circe._
import io.k8s.api.apps.v1.{StatefulSet, StatefulSetList}
import org.http4s.client.Client
import org.http4s.implicits._

private[client] case class StatefulSetsApi[F[_]](
    httpClient: Client[F],
    config: KubeConfig,
    labels: Map[String, String] = Map.empty
)(
    implicit
    val F: Sync[F],
    val listDecoder: Decoder[StatefulSetList],
    encoder: Encoder[StatefulSet],
    decoder: Decoder[StatefulSet]
) extends Listable[F, StatefulSetList]
    with Filterable[StatefulSetsApi[F]] {
  val resourceUri = uri"/apis" / "apps" / "v1" / "statefulsets"

  def namespace(namespace: String) = NamespacedStatefulSetsApi(httpClient, config, namespace)

  override def withLabels(labels: Map[String, String]): StatefulSetsApi[F] =
    StatefulSetsApi(httpClient, config, labels)
}

private[client] case class NamespacedStatefulSetsApi[F[_]](
    httpClient: Client[F],
    config: KubeConfig,
    namespace: String,
    labels: Map[String, String] = Map.empty
)(
    implicit
    val F: Sync[F],
    val resourceEncoder: Encoder[StatefulSet],
    val resourceDecoder: Decoder[StatefulSet],
    val listDecoder: Decoder[StatefulSetList]
) extends Creatable[F, StatefulSet]
    with Replaceable[F, StatefulSet]
    with Gettable[F, StatefulSet]
    with Listable[F, StatefulSetList]
    with Deletable[F]
    with DeletableTerminated[F]
    with GroupDeletable[F]
    with Watchable[F, StatefulSet]
    with Filterable[NamespacedStatefulSetsApi[F]] {
  val resourceUri = uri"/apis" / "apps" / "v1" / "namespaces" / namespace / "statefulsets"

  override def withLabels(labels: Map[String, String]): NamespacedStatefulSetsApi[F] =
    NamespacedStatefulSetsApi(httpClient, config, namespace, labels)
}
