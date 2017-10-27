package com.goyeau.kubernetesclient

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import io.circe._
import io.k8s.api.batch.v1beta1.{CronJob, CronJobList}

private[kubernetesclient] case class CronJobsOperations(protected val config: KubeConfig)(
  implicit protected val system: ActorSystem,
  protected val resourceDecoder: Decoder[CronJobList],
  encoder: Encoder[CronJob],
  decoder: Decoder[CronJob]
) extends Listable[CronJobList] {
  protected val resourceUri = s"${config.server}/apis/batch/v1beta1/cronjobs"

  def namespace(namespace: String) = NamespacedCronJobsOperations(config, namespace)
}

private[kubernetesclient] case class NamespacedCronJobsOperations(protected val config: KubeConfig,
                                                                  protected val namespace: String)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[CronJob],
  decoder: Decoder[CronJob],
  protected val resourceDecoder: Decoder[CronJobList]
) extends Creatable[CronJob]
    with Listable[CronJobList]
    with GroupDeletable {
  protected val resourceUri = s"${config.server}/apis/batch/v1beta1/namespaces/$namespace/cronjobs"

  def apply(cronJobName: String) = CronJobOperations(config, s"$resourceUri/$cronJobName")
}

private[kubernetesclient] case class CronJobOperations(protected val config: KubeConfig,
                                                       protected val resourceUri: Uri)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[CronJob],
  protected val resourceDecoder: Decoder[CronJob]
) extends Gettable[CronJob]
    with Replaceable[CronJob]
    with Deletable
