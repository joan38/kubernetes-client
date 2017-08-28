package com.goyeau.kubernetesclient

import java.io.File

import scala.io.Source

import com.typesafe.scalalogging.LazyLogging
import net.jcazevedo.moultingyaml._

case class Config(apiVersion: String,
                  clusters: Seq[NamedCluster],
                  contexts: Seq[NamedContext],
                  users: Seq[NamedAuthInfo])

case class NamedCluster(name: String, cluster: Cluster)
case class Cluster(server: String,
                   `certificate-authority`: Option[String] = None,
                   `certificate-authority-data`: Option[String] = None)

case class NamedContext(name: String, context: Context)
case class Context(cluster: String,
                   user: String,
                   namespace: Option[String] = None)

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

  implicit val configFormat = yamlFormat4(Config)

  def fromKubeConfigFile(kubeconfig: File,
                         clusterNameMaybe: Option[String]): KubeConfig = {
    val config =
      Source.fromFile(kubeconfig).mkString.parseYaml.convertTo[Config]

    val namedCluster = clusterNameMaybe.fold {
      config.clusters.headOption.getOrElse(
        throw new IllegalArgumentException(s"No cluster in $kubeconfig"))
    } { clusterName =>
      config.clusters
        .find(_.name == clusterName)
        .getOrElse(throw new IllegalArgumentException(
          s"Can't find cluster named $clusterName in $kubeconfig"))
    }
    logger.debug(s"KubeConfig for cluster ${namedCluster.name}")

    val cluster = namedCluster.cluster
    val context = config.contexts
      .map(_.context)
      .find(_.cluster == namedCluster.name)
      .getOrElse(
        throw new IllegalArgumentException(
          s"No context named ${namedCluster.name} in $kubeconfig")
      )
    val user = config.users
      .find(_.name == context.user)
      .getOrElse(
        throw new IllegalArgumentException(
          s"No user named ${context.user} in $kubeconfig")
      )
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
