package com.goyeau.kubernetes.client.util

import org.http4s.Uri

object Uris {
  def addLabels(labels: Map[String, String], uri: Uri): Uri =
    if (labels.nonEmpty) uri.+?("labelSelector" -> labels.map { case (k, v) => s"$k=$v" }.mkString(","))
    else uri
}
