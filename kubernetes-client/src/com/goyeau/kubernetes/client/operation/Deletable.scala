package com.goyeau.kubernetes.client.operation

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.util.CirceEntityCodec.*
import io.k8s.apimachinery.pkg.apis.meta.v1.DeleteOptions
import org.http4s.*
import org.http4s.Method.*
import org.http4s.client.Client
import org.http4s.headers.{`Content-Type`, Authorization}

private[client] trait Deletable[F[_]] {
  protected def httpClient: Client[F]
  implicit protected val F: Async[F]
  protected def config: KubeConfig[F]
  protected def authorization: Option[F[Authorization]]
  protected def resourceUri: Uri

  def delete(name: String, deleteOptions: Option[DeleteOptions] = None): F[Status] = {
    val encoder: EntityEncoder[F, Option[DeleteOptions]] =
      EntityEncoder.encodeBy(`Content-Type`(MediaType.application.json)) { maybeOptions =>
        maybeOptions.fold(Entity[F](EmptyBody.covary[F], Some(0L)))(EntityEncoder[F, DeleteOptions].toEntity(_))
      }

    httpClient.status(
      Request[F](method = DELETE, uri = config.server.resolve(resourceUri) / name)
        .withEntity(deleteOptions)(encoder)
        .withOptionalAuthorization(authorization)
    )
  }
}
