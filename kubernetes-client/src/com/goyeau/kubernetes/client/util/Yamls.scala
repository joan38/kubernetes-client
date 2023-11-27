package com.goyeau.kubernetes.client.util

import cats.effect.Concurrent
import cats.implicits.*
import com.goyeau.kubernetes.client.KubeConfig
import fs2.io.file.{Files, Path}
import io.circe.generic.semiauto.*
import io.circe.scalayaml.parser.*
import io.circe.{Codec, Decoder, Encoder}
import org.http4s.Uri
import org.typelevel.log4cats.Logger

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
    `client-key-data`: Option[String] = None,
    exec: Option[AuthInfoExec] = None
)

case class AuthInfoExecEnv(
    name: String,
    value: String
)

case class AuthInfoExec(
    apiVersion: String,
    command: String,
    env: Option[Seq[AuthInfoExecEnv]],
    args: Option[List[String]],
    installHint: Option[String],
    provideClusterInfo: Option[Boolean],
    interactiveMode: Option[String]
)
case class ExecCredential(
    kind: String,
    apiVersion: String,
    status: ExecCredentialStatus
)
case class ExecCredentialStatus(
    expirationTimestamp: String,
    token: Option[String]
)

private[client] object Yamls {

  def fromKubeConfigFile[F[_]: Concurrent: Logger: Files](
      kubeconfig: Path,
      contextMaybe: Option[String]
  ): F[KubeConfig[F]] =
    for {
      configString <- Text.readFile(kubeconfig)
      configJson   <- Concurrent[F].fromEither(parse(configString))
      config       <- Concurrent[F].fromEither(configJson.as[Config])
      contextName = contextMaybe.getOrElse(config.`current-context`)
      namedContext <-
        config.contexts
          .find(_.name == contextName)
          .liftTo[F](new IllegalArgumentException(s"Can't find context named $contextName in $kubeconfig"))
      _ <- Logger[F].debug(s"KubeConfig: $kubeconfig with context ${namedContext.name}")
      context = namedContext.context

      namedCluster <-
        config.clusters
          .find(_.name == context.cluster)
          .liftTo[F](new IllegalArgumentException(s"Can't find cluster named ${context.cluster} in $kubeconfig"))
      cluster = namedCluster.cluster

      namedAuthInfo <-
        config.users
          .find(_.name == context.user)
          .liftTo[F](new IllegalArgumentException(s"Can't find user named ${context.user} in $kubeconfig"))
      user = namedAuthInfo.user

      server <- Concurrent[F].fromEither(Uri.fromString(cluster.server))
      config <- KubeConfig.of[F](
        server = server,
        caCertData = cluster.`certificate-authority-data`,
        caCertFile = cluster.`certificate-authority`.map(Path(_)),
        clientCertData = user.`client-certificate-data`,
        clientCertFile = user.`client-certificate`.map(Path(_)),
        clientKeyData = user.`client-key-data`,
        clientKeyFile = user.`client-key`.map(Path(_)),
        authInfoExec = user.exec
      )
      _ <- Logger[F].debug(
        s"KubeConfig created, context: $contextName, cluster: ${context.cluster}, user: ${context.user}, server: $server"
      )
    } yield config

  implicit lazy val configDecoder: Decoder[Config]          = deriveDecoder
  implicit lazy val configEncoder: Encoder.AsObject[Config] = deriveEncoder

  implicit lazy val clusterDecoder: Decoder[Cluster]                    = deriveDecoder
  implicit lazy val clusterEncoder: Encoder.AsObject[Cluster]           = deriveEncoder
  implicit lazy val namedClusterDecoder: Decoder[NamedCluster]          = deriveDecoder
  implicit lazy val namedClusterEncoder: Encoder.AsObject[NamedCluster] = deriveEncoder

  implicit lazy val contextDecoder: Decoder[Context]                    = deriveDecoder
  implicit lazy val contextEncoder: Encoder.AsObject[Context]           = deriveEncoder
  implicit lazy val namedContextDecoder: Decoder[NamedContext]          = deriveDecoder
  implicit lazy val namedContextEncoder: Encoder.AsObject[NamedContext] = deriveEncoder

  implicit lazy val authInfoDecoder: Decoder[AuthInfo]                     = deriveDecoder
  implicit lazy val authInfoEncoder: Encoder.AsObject[AuthInfo]            = deriveEncoder
  implicit lazy val authInfoExecEnvCodec: Codec[AuthInfoExecEnv]           = deriveCodec
  implicit lazy val authInfoExecCodec: Codec[AuthInfoExec]                 = deriveCodec
  implicit lazy val namedAuthInfoDecoder: Decoder[NamedAuthInfo]           = deriveDecoder
  implicit lazy val namedAuthInfoEncoder: Encoder.AsObject[NamedAuthInfo]  = deriveEncoder
  implicit lazy val execCredentialStatusCodec: Codec[ExecCredentialStatus] = deriveCodec
  implicit lazy val execCredentialCodec: Codec[ExecCredential]             = deriveCodec
}
