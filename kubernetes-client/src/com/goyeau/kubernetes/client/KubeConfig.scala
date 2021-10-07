package com.goyeau.kubernetes.client

import java.io.File
import cats.ApplicativeThrow
import cats.effect.Sync
import com.goyeau.kubernetes.client.util.Yamls
import org.typelevel.log4cats.Logger
import org.http4s.Uri
import org.http4s.headers.Authorization

case class KubeConfig private (
    server: Uri,
    authorization: Option[Authorization],
    caCertData: Option[String],
    caCertFile: Option[File],
    clientCertData: Option[String],
    clientCertFile: Option[File],
    clientKeyData: Option[String],
    clientKeyFile: Option[File],
    clientKeyPass: Option[String]
)

object KubeConfig {

  @deprecated(message = "Use fromFile instead", since = "0.4.1")
  def apply[F[_]: Sync: Logger](kubeconfig: File): F[KubeConfig] = fromFile(kubeconfig)
  def fromFile[F[_]: Sync: Logger](kubeconfig: File): F[KubeConfig] = Yamls.fromKubeConfigFile(kubeconfig, None)

  @deprecated(message = "Use fromFile instead", since = "0.4.1")
  def apply[F[_]: Sync: Logger](kubeconfig: File, contextName: String): F[KubeConfig] =
    fromFile(kubeconfig, contextName)
  def fromFile[F[_]: Sync: Logger](kubeconfig: File, contextName: String): F[KubeConfig] =
    Yamls.fromKubeConfigFile(kubeconfig, Option(contextName))

  def of[F[_]: ApplicativeThrow](
      server: Uri,
      authorization: Option[Authorization] = None,
      caCertData: Option[String] = None,
      caCertFile: Option[File] = None,
      clientCertData: Option[String] = None,
      clientCertFile: Option[File] = None,
      clientKeyData: Option[String] = None,
      clientKeyFile: Option[File] = None,
      clientKeyPass: Option[String] = None
  ): F[KubeConfig] = {
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
    } yield new KubeConfig(
      server,
      authorization,
      caCertData,
      caCertFile,
      clientCertData,
      clientCertFile,
      clientKeyData,
      clientKeyFile,
      clientKeyPass
    )
    ApplicativeThrow[F].fromEither(configOrError)
  }
}
