package com.goyeau.kubernetes.client.operation

import cats.syntax.all.*
import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.util.Uris.addLabels
import org.http4s.*
import org.http4s.client.Client
import org.http4s.Method.*
import org.http4s.headers.Authorization

private[client] trait GroupDeletable[F[_]] {
  protected def httpClient: Client[F]
  implicit protected val F: Async[F]
  protected def config: KubeConfig[F]
  protected def authorization: Option[F[Authorization]]
  protected def resourceUri: Uri

  def deleteAll(labels: Map[String, String] = Map.empty): F[Status] =
    Request[F](
      DELETE,
      uri = addLabels(labels, config.server.resolve(resourceUri))
    ).withOptionalAuthorization(authorization).flatMap(httpClient.status(_))

}
