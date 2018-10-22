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
  caCertData: Option[String] = None,
  caCertFile: Option[File] = None,
  clientCertData: Option[String] = None,
  clientCertFile: Option[File] = None,
  clientKeyData: Option[String] = None,
  clientKeyFile: Option[File] = None,
  clientKeyPass: Option[String] = None
) {
  require(caCertData.isEmpty || caCertFile.isEmpty, "caCertData and caCertFile can't be set at the same time")
  require(
    clientCertData.isEmpty || clientCertFile.isEmpty,
    "clientCertData and clientCertFile can't be set at the same time"
  )
}

object KubeConfig {

  def apply[F[_]: Sync: Logger](kubeconfig: File): F[KubeConfig] = Yamls.fromKubeConfigFile(kubeconfig, None)

  def apply[F[_]: Sync: Logger](kubeconfig: File, contextName: String): F[KubeConfig] =
    Yamls.fromKubeConfigFile(kubeconfig, Option(contextName))
}
