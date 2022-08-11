package com.goyeau.kubernetes.client.operation

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.util.CirceEntityCodec._
import com.goyeau.kubernetes.client.util.{CachedExecToken, EnrichedStatus}
import io.k8s.apimachinery.pkg.apis.meta.v1.DeleteOptions
import org.http4s._
import org.http4s.Method._
import org.http4s.client.Client
import org.http4s.headers.`Content-Type`

private[client] trait Deletable[F[_]] {
  protected def httpClient: Client[F]
  implicit protected val F: Async[F]
  protected def config: KubeConfig
  protected def cachedExecToken: Option[CachedExecToken[F]]
  protected def resourceUri: Uri

  def delete(name: String, deleteOptions: Option[DeleteOptions] = None): F[Status] = {
    val encoder: EntityEncoder[F, Option[DeleteOptions]] =
      EntityEncoder.encodeBy(`Content-Type`(MediaType.application.json)) { maybeOptions =>
        maybeOptions.fold(Entity[F](EmptyBody.covary[F], Some(0L)))(EntityEncoder[F, DeleteOptions].toEntity(_))
      }

    httpClient
      .runF(
        Request[F](method = DELETE, uri = config.server.resolve(resourceUri) / name)
          .withEntity(deleteOptions)(encoder)
          .withOptionalAuthorization(config.authorization, cachedExecToken)
      )
      .use(EnrichedStatus[F])
  }
}
