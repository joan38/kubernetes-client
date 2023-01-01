package com.goyeau.kubernetes.client.operation

import cats.syntax.all.*
import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import org.http4s.*
import org.http4s.client.Client
import org.http4s.EntityDecoder
import org.http4s.Uri.Path
import org.http4s.headers.`Content-Type`

private[client] trait Proxy[F[_]] {
  protected def httpClient: Client[F]
  implicit protected val F: Async[F]
  protected def config: KubeConfig[F]
  protected def resourceUri: Uri

  def proxy(
      name: String,
      method: Method,
      path: Path,
      contentType: `Content-Type` = `Content-Type`(MediaType.text.plain),
      data: Option[String] = None
  ): F[String] =
    config.authorization
      .fold(Headers.empty.pure[F])(_.map(authorization => Headers(authorization)))
      .flatMap { headers =>
        httpClient.expect[String](
          Request(
            method,
            (config.server.resolve(resourceUri) / name / "proxy").addPath(path.toRelative.renderString),
            headers = headers,
            body = data.fold[EntityBody[F]](EmptyBody)(
              implicitly[EntityEncoder[F, String]].withContentType(contentType).toEntity(_).body
            )
          )
        )(EntityDecoder.text)
      }

}
