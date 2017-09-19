/*
 * Copyright 2017 Joan Goyeau (http://goyeau.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
