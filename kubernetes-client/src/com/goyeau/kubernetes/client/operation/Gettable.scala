package com.goyeau.kubernetes.client.operation

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.util.CirceEntityCodec.*
import io.circe.*
import org.http4s.*
import org.http4s.client.Client
import org.http4s.Method.*
import org.http4s.headers.Authorization

private[client] trait Gettable[F[_], Resource] {
  protected def httpClient: Client[F]
  implicit protected val F: Async[F]
  protected def config: KubeConfig[F]
  protected def authorization: Option[F[Authorization]]
  protected def resourceUri: Uri
  implicit protected def resourceDecoder: Decoder[Resource]

  def get(name: String): F[Resource] =
    httpClient.expectF[Resource](
      Request[F](GET, config.server.resolve(resourceUri) / name)
        .withOptionalAuthorization(authorization)
    )
}
