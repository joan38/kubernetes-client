package com.goyeau.kubernetes.client.operation

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.util.CirceEntityCodec._
import io.circe._
import org.http4s._
import org.http4s.client.Client
import org.http4s.Method._

private[client] trait Gettable[F[_], Resource] {
  protected def httpClient: Client[F]
  implicit protected val F: Async[F]
  protected def config: KubeConfig
  protected def resourceUri: Uri
  implicit protected def resourceDecoder: Decoder[Resource]

  def get(name: String): F[Resource] =
    httpClient.expect[Resource](
      Request[F](GET, config.server.resolve(resourceUri) / name).withOptionalAuthorization(config.authorization)
    )
}
