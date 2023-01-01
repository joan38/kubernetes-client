package com.goyeau.kubernetes.client.api

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import com.goyeau.kubernetes.client.util.CachedExecToken
import io.circe.{Decoder, Encoder}
import io.k8s.apiextensionsapiserver.pkg.apis.apiextensions.v1.{CustomResourceDefinition, CustomResourceDefinitionList}
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.implicits._

private[client] class CustomResourceDefinitionsApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val cachedExecToken: Option[CachedExecToken[F]]
)(implicit
    val F: Async[F],
    val listDecoder: Decoder[CustomResourceDefinitionList],
    val resourceEncoder: Encoder[CustomResourceDefinition],
    val resourceDecoder: Decoder[CustomResourceDefinition]
) extends Creatable[F, CustomResourceDefinition]
    with Replaceable[F, CustomResourceDefinition]
    with Gettable[F, CustomResourceDefinition]
    with Listable[F, CustomResourceDefinitionList]
    with Deletable[F]
    with DeletableTerminated[F]
    with GroupDeletable[F]
    with Watchable[F, CustomResourceDefinition] { self =>
  val resourceUri: Uri = uri"/apis" / "apiextensions.k8s.io" / "v1" / "customresourcedefinitions"
  override val watchResourceUri: Uri =
    uri"/apis" / "apiextensions.k8s.io" / "v1" / "watch" / "customresourcedefinitions"
}
