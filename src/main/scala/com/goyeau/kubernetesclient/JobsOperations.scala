package com.goyeau.kubernetesclient

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import io.circe._
import io.circe.generic.auto._
import io.k8s.api.batch.v1.Job

private[kubernetesclient] case class JobsOperations(config: KubeConfig, private val namespace: String)(
  implicit val system: ActorSystem,
  val encoder: Encoder[Job]
) extends Creatable[Job]
    with GroupDeletable {
  val resourceUri = s"${config.server}/apis/batch/v1/namespaces/$namespace/jobs"

  def apply(jobName: String) = JobOperations(config, s"$resourceUri/$jobName")
}

private[kubernetesclient] case class JobOperations(config: KubeConfig, resourceUri: Uri)(
  implicit val system: ActorSystem,
  val decoder: Decoder[Job]
) extends Gettable[Job]
    with Deletable
