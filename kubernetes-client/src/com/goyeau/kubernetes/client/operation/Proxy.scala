package com.goyeau.kubernetes.client.operation

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import org.http4s._
import org.http4s.client.Client
import org.http4s.EntityDecoder
import org.http4s.Uri.Path
import org.http4s.headers.`Content-Type`

private[client] trait Proxy[F[_]] {
  protected def httpClient: Client[F]
  implicit protected val F: Async[F]
  protected def config: KubeConfig
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
        //TODO: I think this is wrong (hamnis)
        config.server.resolve(resourceUri) / name / s"proxy$path",
        headers = Headers(config.authorization.toList),
        body = data.fold[EntityBody[F]](EmptyBody)(
          implicitly[EntityEncoder[F, String]].withContentType(contentType).toEntity(_).body
        )
      )
    )(EntityDecoder.text)
}
