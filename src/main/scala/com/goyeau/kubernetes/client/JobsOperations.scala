package com.goyeau.kubernetes.client

import akka.actor.ActorSystem
import io.circe._
import io.k8s.api.batch.v1.{Job, JobList}

private[client] case class JobsOperations(protected val config: KubeConfig)(
  implicit protected val system: ActorSystem,
  protected val listDecoder: Decoder[JobList],
  encoder: Encoder[Job],
  decoder: Decoder[Job]
) extends Listable[JobList] {
  protected val resourceUri = "apis/batch/v1/jobs"

  def namespace(namespace: String) = NamespacedJobsOperations(config, namespace)
}

private[client] case class NamespacedJobsOperations(
  protected val config: KubeConfig,
  protected val namespace: String
)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[Job],
  protected val resourceDecoder: Decoder[Job],
  protected val listDecoder: Decoder[JobList]
) extends Creatable[Job]
    with Replaceable[Job]
    with Gettable[Job]
    with Listable[JobList]
    with Deletable
    with GroupDeletable {
  protected val resourceUri = s"apis/batch/v1/namespaces/$namespace/jobs"
}
