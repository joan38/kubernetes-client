package com.goyeau.kubernetes.client.api

import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import io.circe._
import io.k8s.api.batch.v1.{Job, JobList}
import org.http4s.client.Client
import org.http4s.implicits._

private[client] case class JobsApi[F[_]](
    httpClient: Client[F],
    config: KubeConfig,
    labels: Map[String, String] = Map.empty
)(
    implicit
    val F: Sync[F],
    val listDecoder: Decoder[JobList],
    encoder: Encoder[Job],
    decoder: Decoder[Job]
) extends Listable[F, JobList]
    with LabelSelector[JobsApi[F]] {
  val resourceUri = uri"/apis" / "batch" / "v1" / "jobs"

  def namespace(namespace: String) = NamespacedJobsApi(httpClient, config, namespace)

  override def withLabels(labels: Map[String, String]): JobsApi[F] = JobsApi(httpClient, config, labels)
}

private[client] case class NamespacedJobsApi[F[_]](
    httpClient: Client[F],
    config: KubeConfig,
    namespace: String,
    labels: Map[String, String] = Map.empty
)(
    implicit
    val F: Sync[F],
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
    with Watchable[F, Job]
    with LabelSelector[NamespacedJobsApi[F]] {
  val resourceUri = uri"/apis" / "batch" / "v1" / "namespaces" / namespace / "jobs"

  override def withLabels(labels: Map[String, String]): NamespacedJobsApi[F] =
    NamespacedJobsApi(httpClient, config, namespace, labels)
}
