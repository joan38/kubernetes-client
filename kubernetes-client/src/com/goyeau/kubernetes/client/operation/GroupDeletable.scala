package com.goyeau.kubernetes.client.operation

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.util.cache.TokenCache
import com.goyeau.kubernetes.client.util.Uris.addLabels
import org.http4s._
import org.http4s.client.Client
import org.http4s.Method._

private[client] trait GroupDeletable[F[_]] {
  protected def httpClient: Client[F]
  implicit protected val F: Async[F]
  protected def config: KubeConfig[F]
  protected def cachedExecToken: Option[TokenCache[F]]
  protected def resourceUri: Uri

  def deleteAll(labels: Map[String, String] = Map.empty): F[Status] = {
    val uri = addLabels(labels, config.server.resolve(resourceUri))
    httpClient.status(Request[F](DELETE, uri).withOptionalAuthorization(cachedExecToken))
  }
}
