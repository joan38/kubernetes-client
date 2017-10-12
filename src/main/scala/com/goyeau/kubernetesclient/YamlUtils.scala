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
  implicit val clusterFormat: YamlFormat[Cluster] = yamlFormat3(Cluster)
  implicit val namedClusterFormat: YamlFormat[NamedCluster] = yamlFormat2(NamedCluster)

  implicit val contextFormat: YamlFormat[Context] = yamlFormat3(Context)
  implicit val namedContextFormat: YamlFormat[NamedContext] = yamlFormat2(NamedContext)

  implicit val authInfoFormat: YamlFormat[AuthInfo] = yamlFormat4(AuthInfo)
  implicit val namedAuthInfoFormat: YamlFormat[NamedAuthInfo] = yamlFormat2(NamedAuthInfo)

  implicit val configFormat: YamlFormat[Config] = yamlFormat5(Config)

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
