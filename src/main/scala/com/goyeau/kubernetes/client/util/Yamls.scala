package com.goyeau.kubernetes.client.util

import java.io.File

import cats.effect.Sync
import cats.implicits._
import com.goyeau.kubernetes.client.KubeConfig

import scala.io.Source
import io.chrisdavenport.log4cats.Logger
import io.circe.{Decoder, ObjectEncoder}
import io.circe.generic.semiauto._
import io.circe.yaml.parser._
import org.http4s.Uri

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

private[client] object Yamls {

  def fromKubeConfigFile[F[_]: Sync: Logger](kubeconfig: File, contextMaybe: Option[String]): F[KubeConfig] =
    for {
      configJson <- Sync[F].fromEither(parse(Source.fromFile(kubeconfig).mkString))
      config <- Sync[F].fromEither(configJson.as[Config])
      contextName = contextMaybe.getOrElse(config.`current-context`)
      namedContext <- config.contexts
        .find(_.name == contextName)
        .liftTo[F](new IllegalArgumentException(s"Can't find context named $contextName in $kubeconfig"))
      _ <- Logger[F].debug(s"KubeConfig with context ${namedContext.name}")
      context = namedContext.context

      namedCluster <- config.clusters
        .find(_.name == context.cluster)
        .liftTo[F](new IllegalArgumentException(s"Can't find cluster named ${context.cluster} in $kubeconfig"))
      cluster = namedCluster.cluster

      namedAuthInfo <- config.users
        .find(_.name == context.user)
        .liftTo[F](new IllegalArgumentException(s"Can't find user named ${context.user} in $kubeconfig"))
      user = namedAuthInfo.user

      server <- Sync[F].fromEither(Uri.fromString(cluster.server))
    } yield
      KubeConfig(
        server = server,
        caCertData = cluster.`certificate-authority-data`,
        caCertFile = cluster.`certificate-authority`.map(new File(_)),
        clientCertData = user.`client-certificate-data`,
        clientCertFile = user.`client-certificate`.map(new File(_)),
        clientKeyData = user.`client-key-data`,
        clientKeyFile = user.`client-key`.map(new File(_))
      )

  implicit lazy val configDecoder: Decoder[Config] = deriveDecoder
  implicit lazy val configEncoder: ObjectEncoder[Config] = deriveEncoder

  implicit lazy val clusterDecoder: Decoder[Cluster] = deriveDecoder
  implicit lazy val clusterEncoder: ObjectEncoder[Cluster] = deriveEncoder
  implicit lazy val namedClusterDecoder: Decoder[NamedCluster] = deriveDecoder
  implicit lazy val namedClusterEncoder: ObjectEncoder[NamedCluster] = deriveEncoder

  implicit lazy val contextDecoder: Decoder[Context] = deriveDecoder
  implicit lazy val contextEncoder: ObjectEncoder[Context] = deriveEncoder
  implicit lazy val namedContextDecoder: Decoder[NamedContext] = deriveDecoder
  implicit lazy val namedContextEncoder: ObjectEncoder[NamedContext] = deriveEncoder

  implicit lazy val authInfoDecoder: Decoder[AuthInfo] = deriveDecoder
  implicit lazy val authInfoEncoder: ObjectEncoder[AuthInfo] = deriveEncoder
  implicit lazy val namedAuthInfoDecoder: Decoder[NamedAuthInfo] = deriveDecoder
  implicit lazy val namedAuthInfoEncoder: ObjectEncoder[NamedAuthInfo] = deriveEncoder
}
