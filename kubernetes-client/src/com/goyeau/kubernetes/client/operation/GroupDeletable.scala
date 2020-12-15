package com.goyeau.kubernetes.client.operation

import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.util.EnrichedStatus
import com.goyeau.kubernetes.client.util.Uris.addLabels
import org.http4s._
import org.http4s.client.Client
import org.http4s.Method._

private[client] trait GroupDeletable[F[_]] {
  protected def httpClient: Client[F]
  implicit protected val F: Sync[F]
  protected def config: KubeConfig
  protected def resourceUri: Uri

  def deleteAll(labels: Map[String, String] = Map.empty): F[Status] = {
    val uri = addLabels(labels, config.server.resolve(resourceUri))
    httpClient.run(Request[F](DELETE, uri).putHeaders(config.authorization.toSeq: _*)).use(EnrichedStatus[F])
  }
}
