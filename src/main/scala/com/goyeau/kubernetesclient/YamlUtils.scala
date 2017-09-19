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

import java.io.File

import scala.io.Source

import com.typesafe.scalalogging.LazyLogging
import net.jcazevedo.moultingyaml._

case class Config(apiVersion: String,
                  clusters: Seq[NamedCluster],
                  contexts: Seq[NamedContext],
                  `current-context`: String,
                  users: Seq[NamedAuthInfo])

case class NamedCluster(name: String, cluster: Cluster)
case class Cluster(server: String,
                   `certificate-authority`: Option[String] = None,
                   `certificate-authority-data`: Option[String] = None)

case class NamedContext(name: String, context: Context)
case class Context(cluster: String, user: String, namespace: Option[String] = None)

case class NamedAuthInfo(name: String, user: AuthInfo)
case class AuthInfo(`client-certificate`: Option[String] = None,
                    `client-certificate-data`: Option[String] = None,
                    `client-key`: Option[String] = None,
                    `client-key-data`: Option[String] = None)

object YamlUtils extends DefaultYamlProtocol with LazyLogging {
  implicit val clusterFormat = yamlFormat3(Cluster)
  implicit val namedClusterFormat = yamlFormat2(NamedCluster)

  implicit val contextFormat = yamlFormat3(Context)
  implicit val namedContextFormat = yamlFormat2(NamedContext)

  implicit val authInfoFormat = yamlFormat4(AuthInfo)
  implicit val namedAuthInfoFormat = yamlFormat2(NamedAuthInfo)

  implicit val configFormat = yamlFormat5(Config)

  def fromKubeConfigFile(kubeconfig: File, contextMaybe: Option[String]): KubeConfig = {
    val config =
      Source.fromFile(kubeconfig).mkString.parseYaml.convertTo[Config]

    val contextName = contextMaybe.getOrElse(config.`current-context`)
    val namedContext = config.contexts
      .find(_.name == contextName)
      .getOrElse(throw new IllegalArgumentException(s"Can't find context named $contextName in $kubeconfig"))
    logger.debug(s"KubeConfig with context ${namedContext.name}")
    val context = namedContext.context

    val cluster = config.clusters
      .find(_.name == context.cluster)
      .getOrElse(throw new IllegalArgumentException(s"Can't find cluster named ${context.cluster} in $kubeconfig"))
      .cluster

    val user = config.users
      .find(_.name == context.user)
      .getOrElse(throw new IllegalArgumentException(s"Can't find user named ${context.user} in $kubeconfig"))
      .user

    KubeConfig(
      server = cluster.server,
      caCertData = cluster.`certificate-authority-data`,
      caCertFile = cluster.`certificate-authority`.map(new File(_)),
      clientCertData = user.`client-certificate-data`,
      clientCertFile = user.`client-certificate`.map(new File(_)),
      clientKeyData = user.`client-key-data`,
      clientKeyFile = user.`client-key`.map(new File(_))
    )
  }
}
