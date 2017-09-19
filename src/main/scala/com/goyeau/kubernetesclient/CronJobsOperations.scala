package com.goyeau.kubernetesclient

import scala.concurrent.{ExecutionContext, Future}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpMethods, Uri}
import akka.stream.Materializer
import io.circe._
import io.circe.parser._
import io.circe.generic.auto._
import io.k8s.api.batch.v2alpha1.{CronJob, CronJobList}

private[kubernetesclient] case class CronJobsOperations(config: KubeConfig, private val namespace: String)(
  implicit val system: ActorSystem,
  val encoder: Encoder[CronJob]
) extends Creatable[CronJob]
    with GroupDeletable {
  val resourceUri = s"${config.server}/apis/batch/v2alpha1/namespaces/$namespace/cronjobs"

  def apply(cronJobName: String) = CronJobOperations(config, s"$resourceUri/$cronJobName")

  def list()(implicit ec: ExecutionContext, mat: Materializer): Future[CronJobList] =
    RequestUtils
      .singleRequest(config, HttpMethods.GET, resourceUri)
      .map(response => decode[CronJobList](response).fold(throw _, identity))
}

private[kubernetesclient] case class CronJobOperations(config: KubeConfig, resourceUri: Uri)(
  implicit val system: ActorSystem,
  val decoder: Decoder[CronJob],
  val encoder: Encoder[CronJob]
) extends Gettable[CronJob]
    with Replaceable[CronJob]
    with Deletable
