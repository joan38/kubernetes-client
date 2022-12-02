package com.goyeau.kubernetes.client.api

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation.*
import com.goyeau.kubernetes.client.util.cache.TokenCache
import io.circe.*
import io.k8s.api.apps.v1.{StatefulSet, StatefulSetList}
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.implicits.*

private[client] class StatefulSetsApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val authCache: Option[TokenCache[F]]
)(implicit
    val F: Async[F],
    val listDecoder: Decoder[StatefulSetList],
    val resourceDecoder: Decoder[StatefulSet],
    encoder: Encoder[StatefulSet]
) extends Listable[F, StatefulSetList]
    with Watchable[F, StatefulSet] {
  val resourceUri: Uri = uri"/apis" / "apps" / "v1" / "statefulsets"

  def namespace(namespace: String): NamespacedStatefulSetsApi[F] =
    new NamespacedStatefulSetsApi(httpClient, config, authCache, namespace)
}

private[client] class NamespacedStatefulSetsApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val authCache: Option[TokenCache[F]],
    namespace: String
)(implicit
    val F: Async[F],
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
