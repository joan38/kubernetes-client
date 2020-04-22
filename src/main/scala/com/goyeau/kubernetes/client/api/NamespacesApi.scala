package com.goyeau.kubernetes.client.api

import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import io.circe.{Decoder, Encoder}
import io.k8s.api.core.v1.{Namespace, NamespaceList}
import org.http4s.client.Client
import org.http4s.implicits._

private[client] case class NamespacesApi[F[_]](
    httpClient: Client[F],
    config: KubeConfig,
    labels: Map[String, String] = Map.empty
)(
    implicit
    val F: Sync[F],
    val listDecoder: Decoder[NamespaceList],
    val resourceEncoder: Encoder[Namespace],
    val resourceDecoder: Decoder[Namespace]
) extends Creatable[F, Namespace]
    with Replaceable[F, Namespace]
    with Gettable[F, Namespace]
    with Listable[F, NamespaceList]
    with Deletable[F]
    with DeletableTerminated[F]
    with Watchable[F, Namespace]
    with LabelSelector[NamespacesApi[F]] {
  protected val resourceUri = uri"/api" / "v1" / "namespaces"

  override def withLabels(labels: Map[String, String]): NamespacesApi[F] =
    NamespacesApi(httpClient, config, labels)
}
