package com.goyeau.kubernetes.client.api

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation.*
import io.circe.{Decoder, Encoder}
import io.k8s.api.core.v1.{Namespace, Node, NodeList}
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.implicits.*

private[client] class NodesApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val authorization: Option[F[Authorization]]
)(implicit
    val F: Async[F],
    val listDecoder: Decoder[NodeList],
    val resourceEncoder: Encoder[Node],
    val resourceDecoder: Decoder[Node]
) extends Gettable[F, Node]
    with Listable[F, NodeList]
    with Watchable[F, Node] {
  protected val resourceUri: Uri = uri"/api" / "v1" / "nodes"
}
