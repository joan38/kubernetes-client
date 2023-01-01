package com.goyeau.kubernetes.client

import java.io.File
import cats.ApplicativeThrow
import cats.data.OptionT
import cats.effect.Sync
import com.goyeau.kubernetes.client.util.{AuthInfoExec, Yamls}
import org.typelevel.log4cats.Logger
import org.http4s.Uri
import org.http4s.headers.Authorization

case class KubeConfig[F[_]] private (
    server: Uri,
    authorization: Option[F[Authorization]],
    caCertData: Option[String],
    caCertFile: Option[File],
    clientCertData: Option[String],
    clientCertFile: Option[File],
    clientKeyData: Option[String],
    clientKeyFile: Option[File],
    clientKeyPass: Option[String],
    authInfoExec: Option[AuthInfoExec]
)

object KubeConfig {

  @deprecated(message = "Use fromFile instead", since = "0.4.1")
  def apply[F[_]: Sync: Logger](kubeconfig: File): F[KubeConfig[F]]    = fromFile(kubeconfig)
  def fromFile[F[_]: Sync: Logger](kubeconfig: File): F[KubeConfig[F]] = Yamls.fromKubeConfigFile(kubeconfig, None)

  @deprecated(message = "Use fromFile instead", since = "0.4.1")
  def apply[F[_]: Sync: Logger](kubeconfig: File, contextName: String): F[KubeConfig[F]] =
    fromFile(kubeconfig, contextName)
  def fromFile[F[_]: Sync: Logger](kubeconfig: File, contextName: String): F[KubeConfig[F]] =
    Yamls.fromKubeConfigFile(kubeconfig, Option(contextName))

  def of[F[_]: ApplicativeThrow](
      server: Uri,
      authorization: Option[F[Authorization]] = None,
      caCertData: Option[String] = None,
      caCertFile: Option[File] = None,
      clientCertData: Option[String] = None,
      clientCertFile: Option[File] = None,
      clientKeyData: Option[String] = None,
      clientKeyFile: Option[File] = None,
      clientKeyPass: Option[String] = None,
      authInfoExec: Option[AuthInfoExec] = None
  ): F[KubeConfig[F]] = {
    val configOrError = for {
      _ <- Either.cond(
        caCertData.isEmpty || caCertFile.isEmpty,
        (),
        new IllegalArgumentException("caCertData and caCertFile can't be set at the same time")
      )
      _ <- Either.cond(
        clientCertData.isEmpty || clientCertFile.isEmpty,
        (),
        new IllegalArgumentException("clientCertData and clientCertFile can't be set at the same time")
      )
      _ <- Either.cond(
        clientKeyData.isEmpty || clientKeyFile.isEmpty,
        (),
        new IllegalArgumentException("clientKeyData and clientKeyFile can't be set at the same time")
      )
      _ <- Either.cond(
        authorization.isEmpty || authInfoExec.isEmpty,
        (),
        new IllegalArgumentException("authorization and authInfoExec can't be set at the same time")
      )
      _ <- Either.cond(
        !authInfoExec.exists(_.interactiveMode.contains("Always")),
        (),
        new IllegalArgumentException("interactiveMode=Always is not supported")
      )
      _ <- Either.cond(
        !authInfoExec.exists(_.provideClusterInfo.contains(true)),
        (),
        new IllegalArgumentException("provideClusterInfo=true is not supported")
      )
    } yield new KubeConfig(
      server,
      authorization,
      caCertData,
      caCertFile,
      clientCertData,
      clientCertFile,
      clientKeyData,
      clientKeyFile,
      clientKeyPass,
      authInfoExec
    )
    ApplicativeThrow[F].fromEither(configOrError)
  }
}
