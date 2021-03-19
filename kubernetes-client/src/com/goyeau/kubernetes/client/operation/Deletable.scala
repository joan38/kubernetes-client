package com.goyeau.kubernetes.client.operation

import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.util.CirceEntityCodec._
import com.goyeau.kubernetes.client.util.EnrichedStatus
import io.k8s.apimachinery.pkg.apis.meta.v1.DeleteOptions
import org.http4s._
import org.http4s.Method._
import org.http4s.client.Client
import org.http4s.headers.`Content-Type`

private[client] trait Deletable[F[_]] {
  protected def httpClient: Client[F]
  implicit protected val F: Sync[F]
  protected def config: KubeConfig
  protected def resourceUri: Uri

  def delete(name: String, deleteOptions: Option[DeleteOptions] = None): F[Status] = {
    implicit val encoder: EntityEncoder[F, Option[DeleteOptions]] =
      EntityEncoder.encodeBy(`Content-Type`(MediaType.application.json)) { opt =>
        opt.fold(Entity.empty.asInstanceOf[Entity[F]])(EntityEncoder[F, DeleteOptions].toEntity(_))
      }

    httpClient
      .run(
        Request[F](
          method = DELETE,
          uri = config.server.resolve(resourceUri) / name
        ).withOptionalAuthorization(config.authorization)
          .withEntity(deleteOptions)(encoder)
      )
      .use(EnrichedStatus[F])
  }
}
