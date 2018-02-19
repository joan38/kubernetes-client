package com.goyeau.kubernetesclient

import akka.http.scaladsl.model.Uri

case class KubernetesException(statusCode: Int, uri: Uri, message: String)
    extends Exception(s"$uri returned $statusCode: $message")
