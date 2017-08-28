package com.goyeau.kubernetesclient

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import io.circe._
import io.circe.generic.auto._
import io.k8s.api.batch.v2alpha1.CronJob

private[kubernetesclient] case class CronJobsOperations(config: KubeConfig, private val namespace: String)(
  implicit val system: ActorSystem,
  val encoder: Encoder[CronJob]
) extends Creatable[CronJob]
    with GroupDeletable {
  val resourceUri = s"${config.server}/apis/batch/v1/namespaces/$namespace/cronjobs"

  def apply(cronJobName: String) = CronJobOperations(config, s"$resourceUri/$cronJobName")
}

private[kubernetesclient] case class CronJobOperations(config: KubeConfig, resourceUri: Uri)(
  implicit val system: ActorSystem,
  val decoder: Decoder[CronJob]
) extends Gettable[CronJob]
    with Deletable
