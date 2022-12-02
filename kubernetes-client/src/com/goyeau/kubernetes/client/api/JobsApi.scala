package com.goyeau.kubernetes.client.api

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation.*
import com.goyeau.kubernetes.client.util.cache.TokenCache
import io.circe._
import io.k8s.api.batch.v1.{Job, JobList}
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.implicits.*

private[client] class JobsApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val cachedExecToken: Option[TokenCache[F]]
)(implicit
    val F: Async[F],
    val listDecoder: Decoder[JobList],
    val resourceDecoder: Decoder[Job],
    encoder: Encoder[Job]
) extends Listable[F, JobList]
    with Watchable[F, Job] {
  val resourceUri: Uri = uri"/apis" / "batch" / "v1" / "jobs"

  def namespace(namespace: String): NamespacedJobsApi[F] =
    new NamespacedJobsApi(httpClient, config, cachedExecToken, namespace)
}

private[client] class NamespacedJobsApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val cachedExecToken: Option[TokenCache[F]],
    namespace: String
)(implicit
    val F: Async[F],
    val resourceEncoder: Encoder[Job],
    val resourceDecoder: Decoder[Job],
    val listDecoder: Decoder[JobList]
) extends Creatable[F, Job]
    with Replaceable[F, Job]
    with Gettable[F, Job]
    with Listable[F, JobList]
    with Deletable[F]
    with DeletableTerminated[F]
    with GroupDeletable[F]
    with Watchable[F, Job] {
  val resourceUri: Uri = uri"/apis" / "batch" / "v1" / "namespaces" / namespace / "jobs"
}
