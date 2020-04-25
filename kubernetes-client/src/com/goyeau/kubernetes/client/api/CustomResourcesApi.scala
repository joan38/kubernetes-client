package com.goyeau.kubernetes.client.api

import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.crd.{CrdContext, CustomResource, CustomResourceList}
import com.goyeau.kubernetes.client.operation._
import com.goyeau.kubernetes.client.util.CirceEntityCodec._
import com.goyeau.kubernetes.client.util.EnrichedStatus
import io.circe._
import org.http4s.Method._
import org.http4s.client.Client
import org.http4s.implicits._
import org.http4s.{Status, Uri}

private[client] case class CustomResourcesApi[F[_], A, B](
    httpClient: Client[F],
    config: KubeConfig,
    context: CrdContext
)(
    implicit
    val F: Sync[F],
    val listDecoder: Decoder[CustomResourceList[A, B]],
    encoder: Encoder[CustomResource[A, B]],
    decoder: Decoder[CustomResource[A, B]]
) extends Listable[F, CustomResourceList[A, B]] {

  val resourceUri: Uri = uri"/apis" / context.group / context.version / context.plural

  def namespace(namespace: String): NamespacedCustomResourcesApi[F, A, B] =
    NamespacedCustomResourcesApi(httpClient, config, context, namespace)
}

private[client] case class NamespacedCustomResourcesApi[F[_], A, B](
    httpClient: Client[F],
    config: KubeConfig,
    context: CrdContext,
    namespace: String
)(
    implicit
    val F: Sync[F],
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
    httpClient.fetch(
      PUT(resource, config.server.resolve(resourceUri / name / "status"), config.authorization.toSeq: _*)
    )(
      EnrichedStatus[F]
    )
}
