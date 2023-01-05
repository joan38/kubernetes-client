package com.goyeau.kubernetes.client.operation

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.util.CirceEntityCodec.*
import com.goyeau.kubernetes.client.util.Uris.addLabels
import io.circe.*
import org.http4s.*
import org.http4s.client.Client
import org.http4s.Method.*
import org.http4s.headers.Authorization

private[client] trait Listable[F[_], Resource] {
  protected def httpClient: Client[F]
  implicit protected val F: Async[F]
  protected def config: KubeConfig[F]
  protected def authorization: Option[F[Authorization]]
  protected def resourceUri: Uri
  implicit protected def listDecoder: Decoder[Resource]

  def list(labels: Map[String, String] = Map.empty): F[Resource] = {
    val uri = addLabels(labels, config.server.resolve(resourceUri))
    httpClient.expectF[Resource](
      Request[F](GET, uri).withOptionalAuthorization(authorization)
    )
  }
}
