package com.goyeau.kubernetes.client.api

import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import io.circe._
import io.k8s.api.batch.v1beta1.{CronJob, CronJobList}
import org.http4s.client.Client
import org.http4s.implicits._

private[client] case class CronJobsApi[F[_]](
    httpClient: Client[F],
    config: KubeConfig,
    labels: Map[String, String] = Map.empty
)(
    implicit
    val F: Sync[F],
    val listDecoder: Decoder[CronJobList],
    encoder: Encoder[CronJob],
    decoder: Decoder[CronJob]
) extends Listable[F, CronJobList]
    with Filterable[CronJobsApi[F]] {
  val resourceUri = uri"/apis" / "batch" / "v1beta1" / "cronjobs"

  def namespace(namespace: String) = NamespacedCronJobsApi(httpClient, config, namespace)

  override def withLabels(labels: Map[String, String]): CronJobsApi[F] =
    CronJobsApi(httpClient, config, labels)
}

private[client] case class NamespacedCronJobsApi[F[_]](
    httpClient: Client[F],
    config: KubeConfig,
    namespace: String,
    labels: Map[String, String] = Map.empty
)(
    implicit
    val F: Sync[F],
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
    with Watchable[F, CronJob]
    with Filterable[NamespacedCronJobsApi[F]] {
  val resourceUri = uri"/apis" / "batch" / "v1beta1" / "namespaces" / namespace / "cronjobs"

  override def withLabels(labels: Map[String, String]): NamespacedCronJobsApi[F] =
    NamespacedCronJobsApi(httpClient, config, namespace, labels)
}
