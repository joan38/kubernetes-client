package com.goyeau.kubernetes.client

import java.io.File

import scala.io.Source

import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.auto._
import io.circe.yaml.parser

case class Config(
  apiVersion: String,
  clusters: Seq[NamedCluster],
  contexts: Seq[NamedContext],
  `current-context`: String,
  users: Seq[NamedAuthInfo]
)

case class NamedCluster(name: String, cluster: Cluster)
case class Cluster(
  server: String,
  `certificate-authority`: Option[String] = None,
  `certificate-authority-data`: Option[String] = None
)

case class NamedContext(name: String, context: Context)
case class Context(cluster: String, user: String, namespace: Option[String] = None)

case class NamedAuthInfo(name: String, user: AuthInfo)
case class AuthInfo(
  `client-certificate`: Option[String] = None,
  `client-certificate-data`: Option[String] = None,
  `client-key`: Option[String] = None,
  `client-key-data`: Option[String] = None
)

object YamlUtils extends LazyLogging {

  def fromKubeConfigFile(kubeconfig: File, contextMaybe: Option[String]): KubeConfig =
    (
      for {
        configJson <- parser.parse(Source.fromFile(kubeconfig).mkString)
        config <- configJson.as[Config]
        contextName = contextMaybe.getOrElse(config.`current-context`)
        namedContext = config.contexts
          .find(_.name == contextName)
          .getOrElse(throw new IllegalArgumentException(s"Can't find context named $contextName in $kubeconfig"))
        _ = logger.debug(s"KubeConfig with context ${namedContext.name}")
        context = namedContext.context

        cluster = config.clusters
          .find(_.name == context.cluster)
          .getOrElse(throw new IllegalArgumentException(s"Can't find cluster named ${context.cluster} in $kubeconfig"))
          .cluster

        user = config.users
          .find(_.name == context.user)
          .getOrElse(throw new IllegalArgumentException(s"Can't find user named ${context.user} in $kubeconfig"))
          .user
      } yield
        KubeConfig(
          server = cluster.server,
          caCertData = cluster.`certificate-authority-data`,
          caCertFile = cluster.`certificate-authority`.map(new File(_)),
          clientCertData = user.`client-certificate-data`,
          clientCertFile = user.`client-certificate`.map(new File(_)),
          clientKeyData = user.`client-key-data`,
          clientKeyFile = user.`client-key`.map(new File(_))
        )
    ).fold(
      error => throw new IllegalArgumentException(s"Parsing config file $kubeconfig failed: ${error.getMessage}"),
      identity
    )
}
