package com.goyeau.kubernetesclient

import java.io.File

import akka.http.scaladsl.model.Uri

case class KubeConfig(
  server: Uri,
  oauthToken: Option[String] = None,
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

  def apply(kubeconfig: File): KubeConfig = YamlUtils.fromKubeConfigFile(kubeconfig, None)

  def apply(kubeconfig: File, contextName: String): KubeConfig =
    YamlUtils.fromKubeConfigFile(kubeconfig, Option(contextName))
}
