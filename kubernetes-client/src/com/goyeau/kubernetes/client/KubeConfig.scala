package com.goyeau.kubernetes.client

import java.io.File

import cats.effect.Sync
import com.goyeau.kubernetes.client.util.Yamls
import io.chrisdavenport.log4cats.Logger
import org.http4s.Uri
import org.http4s.headers.Authorization

case class KubeConfig(
    server: Uri,
    authorization: Option[Authorization] = None,
    caCert: Option[Certificate] = None,
    clientCert: Option[Certificate] = None,
    clientKey: Option[Certificate] = None,
    clientKeyPass: Option[String] = None
)

object KubeConfig {

  @deprecated(message = "Use fromFile instead", since = "0.4.1")
  def apply[F[_]: Sync: Logger](kubeconfig: File): F[KubeConfig]    = fromFile(kubeconfig)
  def fromFile[F[_]: Sync: Logger](kubeconfig: File): F[KubeConfig] = Yamls.fromKubeConfigFile(kubeconfig, None)

  @deprecated(message = "Use fromFile instead", since = "0.4.1")
  def apply[F[_]: Sync: Logger](kubeconfig: File, contextName: String): F[KubeConfig] =
    fromFile(kubeconfig, contextName)
  def fromFile[F[_]: Sync: Logger](kubeconfig: File, contextName: String): F[KubeConfig] =
    Yamls.fromKubeConfigFile(kubeconfig, Option(contextName))
}
