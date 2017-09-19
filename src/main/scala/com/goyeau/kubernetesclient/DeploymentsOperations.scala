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

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import io.circe._
import io.circe.generic.auto._
import io.k8s.api.apps.v1beta1.Deployment

private[kubernetesclient] case class DeploymentsOperations(config: KubeConfig, private val namespace: String)(
  implicit val system: ActorSystem,
  val encoder: Encoder[Deployment]
) extends Creatable[Deployment]
    with GroupDeletable {
  val resourceUri = s"${config.server}/apis/extensions/v1beta1/namespaces/$namespace/deployments"

  def apply(deploymentName: String) = DeploymentOperations(config, s"$resourceUri/$deploymentName")
}

private[kubernetesclient] case class DeploymentOperations(config: KubeConfig, resourceUri: Uri)(
  implicit val system: ActorSystem,
  val decoder: Decoder[Deployment],
  val encoder: Encoder[Deployment]
) extends Gettable[Deployment]
    with Replaceable[Deployment]
    with Deletable
