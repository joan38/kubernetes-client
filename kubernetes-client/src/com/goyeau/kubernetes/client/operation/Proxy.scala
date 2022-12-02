package com.goyeau.kubernetes.client.operation

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation.*
import com.goyeau.kubernetes.client.util.cache.TokenCache
import org.http4s.*
import org.http4s.client.Client
import org.http4s.EntityDecoder
import org.http4s.Uri.Path
import org.http4s.headers.`Content-Type`

private[client] trait Proxy[F[_]] {
  protected def httpClient: Client[F]
  implicit protected val F: Async[F]
  protected def config: KubeConfig[F]
  protected def authCache: Option[TokenCache[F]]
  protected def resourceUri: Uri

  def proxy(
      name: String,
      method: Method,
      path: Path,
      contentType: `Content-Type` = `Content-Type`(MediaType.text.plain),
      data: Option[String] = None
  ): F[String] =
    httpClient.expect[String](
      Request(
        method,
        (config.server.resolve(resourceUri) / name / "proxy").addPath(path.toRelative.renderString),
        body = data.fold[EntityBody[F]](EmptyBody)(
          implicitly[EntityEncoder[F, String]].withContentType(contentType).toEntity(_).body
        )
      ).withOptionalAuthorization(authCache)
    )(EntityDecoder.text)

}
