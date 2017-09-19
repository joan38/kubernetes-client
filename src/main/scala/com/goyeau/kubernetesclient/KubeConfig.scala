/*
 * Copyright 2017 Joan Goyeau (http://goyeau.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.goyeau.kubernetesclient

import java.io.File

import scala.io.Source

import akka.http.scaladsl.model.Uri

case class KubeConfig(server: Uri,
                      oauthToken: Option[String] = None,
                      caCertData: Option[String] = None,
                      caCertFile: Option[File] = None,
                      clientCertData: Option[String] = None,
                      clientCertFile: Option[File] = None,
                      clientKeyData: Option[String] = None,
                      clientKeyFile: Option[File] = None,
                      clientKeyPass: Option[String] = None) {
  require(caCertData.isEmpty || caCertFile.isEmpty, "caCertData and caCertFile can't be set at the same time")
  require(
    clientCertData.isEmpty || clientCertFile.isEmpty,
    "clientCertData and clientCertFile can't be set at the same time"
  )
}

object KubeConfig {

  def apply(kubeconfig: File): KubeConfig = YamlUtils.fromKubeConfigFile(kubeconfig, None)

  def apply(kubeconfig: File, clusterName: String): KubeConfig =
    YamlUtils.fromKubeConfigFile(kubeconfig, Option(clusterName))
}
