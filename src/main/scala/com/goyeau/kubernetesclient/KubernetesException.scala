package com.goyeau.kubernetesclient

import akka.http.scaladsl.model.Uri

class KubernetesException(val statusCode: Int, val uri: Uri, message: String)
    extends Exception(s"$uri returned $statusCode: $message")
