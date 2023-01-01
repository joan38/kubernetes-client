package com.goyeau.kubernetes.client.api

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.crd.{CrdContext, CustomResource, CustomResourceList}
import com.goyeau.kubernetes.client.operation.*
import com.goyeau.kubernetes.client.util.CirceEntityCodec.*
import com.goyeau.kubernetes.client.util.CachedExecToken
import io.circe.*
import org.http4s.Method.*
import org.http4s.client.Client
import org.http4s.implicits.*
import org.http4s.{Request, Status, Uri}

private[client] class CustomResourcesApi[F[_], A, B](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val cachedExecToken: Option[CachedExecToken[F]],
    val context: CrdContext
)(implicit
    val F: Async[F],
    val listDecoder: Decoder[CustomResourceList[A, B]],
    val resourceDecoder: Decoder[CustomResource[A, B]],
    encoder: Encoder[CustomResource[A, B]]
) extends Listable[F, CustomResourceList[A, B]]
    with Watchable[F, CustomResource[A, B]] {

  val resourceUri: Uri = uri"/apis" / context.group / context.version / context.plural

  def namespace(namespace: String): NamespacedCustomResourcesApi[F, A, B] =
    new NamespacedCustomResourcesApi(httpClient, config, cachedExecToken, context, namespace)
}

private[client] class NamespacedCustomResourcesApi[F[_], A, B](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val cachedExecToken: Option[CachedExecToken[F]],
    val context: CrdContext,
    namespace: String
)(implicit
    val F: Async[F],
    val resourceEncoder: Encoder[CustomResource[A, B]],
    val resourceDecoder: Decoder[CustomResource[A, B]],
    val listDecoder: Decoder[CustomResourceList[A, B]]
) extends Creatable[F, CustomResource[A, B]]
    with Replaceable[F, CustomResource[A, B]]
    with Gettable[F, CustomResource[A, B]]
    with Listable[F, CustomResourceList[A, B]]
    with Deletable[F]
    with GroupDeletable[F]
    with Watchable[F, CustomResource[A, B]] {

  val resourceUri: Uri = uri"/apis" / context.group / context.version / "namespaces" / namespace / context.plural

  def updateStatus(name: String, resource: CustomResource[A, B]): F[Status] =
    httpClient.status(
      Request[F](PUT, config.server.resolve(resourceUri / name / "status"))
        .withEntity(resource)
        .withOptionalAuthorization(config.authorization, cachedExecToken)
    )
}
