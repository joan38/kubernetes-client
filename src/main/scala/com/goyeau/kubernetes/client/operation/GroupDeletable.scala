package com.goyeau.kubernetes.client.operation

import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.util.EnrichedStatus
import com.goyeau.kubernetes.client.util.Uris.addLabels
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.Method._

private[client] trait GroupDeletable[F[_]] extends Http4sClientDsl[F] {
  protected def httpClient: Client[F]
  implicit protected val F: Sync[F]
  protected def config: KubeConfig
  protected def resourceUri: Uri
  protected val labels: Map[String, String]

  def delete: F[Status] = {
    val uri = addLabels(labels, config.server.resolve(resourceUri))
    httpClient.fetch(DELETE(uri, config.authorization.toSeq: _*))(EnrichedStatus[F])
  }
}
