package com.goyeau.kubernetes.client.api

import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import io.circe._
import io.k8s.api.apps.v1.{StatefulSet, StatefulSetList}
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.implicits._

private[client] class StatefulSetsApi[F[_]](val httpClient: Client[F], val config: KubeConfig)(implicit
    val F: Sync[F],
    val listDecoder: Decoder[StatefulSetList],
    encoder: Encoder[StatefulSet],
    decoder: Decoder[StatefulSet]
) extends Listable[F, StatefulSetList] {
  val resourceUri: Uri = uri"/apis" / "apps" / "v1" / "statefulsets"

  def namespace(namespace: String): NamespacedStatefulSetsApi[F] =
    new NamespacedStatefulSetsApi(httpClient, config, namespace)
}

private[client] class NamespacedStatefulSetsApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig,
    namespace: String
)(implicit
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
    with Watchable[F, StatefulSet] {
  val resourceUri: Uri = uri"/apis" / "apps" / "v1" / "namespaces" / namespace / "statefulsets"
}
