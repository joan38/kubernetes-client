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
import io.circe._
import io.circe.generic.auto._
import io.k8s.api.core.v1.Namespace

case class KubernetesClient(config: KubeConfig)(implicit system: ActorSystem) {
  lazy val namespaces = NamespacesOperations(config)
}

private[kubernetesclient] case class NamespacesOperations(config: KubeConfig)(
  implicit val system: ActorSystem,
  val encoder: Encoder[Namespace]
) extends Creatable[Namespace] {
  val resourceUri = s"${config.server}/api/v1/namespaces"

  def apply(namespace: String) = NamespaceOperations(config, namespace)
}

private[kubernetesclient] case class NamespaceOperations(private val config: KubeConfig, private val namespace: String)(
  implicit system: ActorSystem
) {
  lazy val pods = PodsOperations(config, namespace)
  lazy val jobs = JobsOperations(config, namespace)
  lazy val cronJobs = CronJobsOperations(config, namespace)
  lazy val deployments = DeploymentsOperations(config, namespace)
  lazy val services = ServicesOperations(config, namespace)
}
