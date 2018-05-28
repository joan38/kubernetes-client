package com.goyeau.kubernetesclient

import akka.actor.ActorSystem
import io.circe._
import io.k8s.api.batch.v1beta1.{CronJob, CronJobList}

private[kubernetesclient] case class CronJobsOperations(protected val config: KubeConfig)(
  implicit protected val system: ActorSystem,
  protected val listDecoder: Decoder[CronJobList],
  encoder: Encoder[CronJob],
  decoder: Decoder[CronJob]
) extends Listable[CronJobList] {
  protected val resourceUri = "apis/batch/v1beta1/cronjobs"

  def namespace(namespace: String) = NamespacedCronJobsOperations(config, namespace)
}

private[kubernetesclient] case class NamespacedCronJobsOperations(
  protected val config: KubeConfig,
  protected val namespace: String
)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[CronJob],
  protected val resourceDecoder: Decoder[CronJob],
  protected val listDecoder: Decoder[CronJobList]
) extends Creatable[CronJob]
    with Replaceable[CronJob]
    with Gettable[CronJob]
    with Listable[CronJobList]
    with Deletable
    with GroupDeletable {
  protected val resourceUri = s"apis/batch/v1beta1/namespaces/$namespace/cronjobs"
}
