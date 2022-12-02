package com.goyeau.kubernetes.client.api

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import com.goyeau.kubernetes.client.util.cache.TokenCache
import io.circe._
import io.k8s.api.batch.v1.{CronJob, CronJobList}
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.implicits._

private[client] class CronJobsApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val authCache: Option[TokenCache[F]]
)(implicit
    val F: Async[F],
    val listDecoder: Decoder[CronJobList],
    val resourceDecoder: Decoder[CronJob],
    encoder: Encoder[CronJob]
) extends Listable[F, CronJobList]
    with Watchable[F, CronJob] {
  val resourceUri: Uri = uri"/apis" / "batch" / "v1beta1" / "cronjobs"

  def namespace(namespace: String): NamespacedCronJobsApi[F] =
    new NamespacedCronJobsApi(httpClient, config, authCache, namespace)
}

private[client] class NamespacedCronJobsApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val authCache: Option[TokenCache[F]],
    namespace: String
)(implicit
    val F: Async[F],
    val resourceEncoder: Encoder[CronJob],
    val resourceDecoder: Decoder[CronJob],
    val listDecoder: Decoder[CronJobList]
) extends Creatable[F, CronJob]
    with Replaceable[F, CronJob]
    with Gettable[F, CronJob]
    with Listable[F, CronJobList]
    with Deletable[F]
    with DeletableTerminated[F]
    with GroupDeletable[F]
    with Watchable[F, CronJob] {
  val resourceUri: Uri = uri"/apis" / "batch" / "v1beta1" / "namespaces" / namespace / "cronjobs"
}
