package com.goyeau.kubernetes.client.api

import cats.effect.{Sync, Timer}
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import io.circe._
import io.k8s.api.apps.v1.{StatefulSet, StatefulSetList}
import org.http4s.client.Client
import org.http4s.Uri.uri

private[client] case class StatefulSetsApi[F[_]](httpClient: Client[F], config: KubeConfig)(
  implicit
  val F: Sync[F],
  timer: Timer[F],
  val listDecoder: Decoder[StatefulSetList],
  encoder: Encoder[StatefulSet],
  decoder: Decoder[StatefulSet]
) extends Listable[F, StatefulSetList] {
  val resourceUri = uri("/apis") / "apps" / "v1" / "statefulsets"

  def namespace(namespace: String) = NamespacedStatefulSetsApi(httpClient, config, namespace)
}

private[client] case class NamespacedStatefulSetsApi[F[_]](
  httpClient: Client[F],
  config: KubeConfig,
  namespace: String
)(
  implicit
  val F: Sync[F],
  val timer: Timer[F],
  val resourceEncoder: Encoder[StatefulSet],
  val resourceDecoder: Decoder[StatefulSet],
  val listDecoder: Decoder[StatefulSetList]
) extends Creatable[F, StatefulSet]
    with Replaceable[F, StatefulSet]
    with Gettable[F, StatefulSet]
    with Listable[F, StatefulSetList]
    with Deletable[F]
    with DeletableTerminated[F]
    with GroupDeletable[F] {
  val resourceUri = uri("/apis") / "apps" / "v1" / "namespaces" / namespace / "statefulsets"
}
