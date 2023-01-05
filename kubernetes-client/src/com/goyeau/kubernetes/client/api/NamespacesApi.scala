package com.goyeau.kubernetes.client.api

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation.*
import io.circe.{Decoder, Encoder}
import io.k8s.api.core.v1.{Namespace, NamespaceList}
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.implicits.*

private[client] class NamespacesApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val authorization: Option[F[Authorization]]
)(implicit
    val F: Async[F],
    val listDecoder: Decoder[NamespaceList],
    val resourceEncoder: Encoder[Namespace],
    val resourceDecoder: Decoder[Namespace]
) extends Creatable[F, Namespace]
    with Replaceable[F, Namespace]
    with Gettable[F, Namespace]
    with Listable[F, NamespaceList]
    with Deletable[F]
    with DeletableTerminated[F]
    with Watchable[F, Namespace] {
  protected val resourceUri: Uri = uri"/api" / "v1" / "namespaces"
}
