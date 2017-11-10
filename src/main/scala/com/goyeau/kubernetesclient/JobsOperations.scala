package com.goyeau.kubernetesclient

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import io.circe._
import io.k8s.api.batch.v1.{Job, JobList}

private[kubernetesclient] case class JobsOperations(protected val config: KubeConfig)(
  implicit protected val system: ActorSystem,
  protected val resourceDecoder: Decoder[JobList],
  encoder: Encoder[Job],
  decoder: Decoder[Job]
) extends Listable[JobList] {
  protected val resourceUri = s"${config.server}/apis/batch/v1/jobs"

  def namespace(namespace: String) = NamespacedJobsOperations(config, namespace)
}

private[kubernetesclient] case class NamespacedJobsOperations(protected val config: KubeConfig,
                                                              protected val namespace: String)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[Job],
  decoder: Decoder[Job],
  protected val resourceDecoder: Decoder[JobList]
) extends Creatable[Job]
    with CreateOrUpdatable[Job]
    with Listable[JobList]
    with GroupDeletable {
  protected val resourceUri = s"${config.server}/apis/batch/v1/namespaces/$namespace/jobs"

  def apply(jobName: String) = JobOperations(config, s"$resourceUri/$jobName")
}

private[kubernetesclient] case class JobOperations(protected val config: KubeConfig, protected val resourceUri: Uri)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[Job],
  protected val resourceDecoder: Decoder[Job]
) extends Gettable[Job]
    with Replaceable[Job]
    with Deletable
