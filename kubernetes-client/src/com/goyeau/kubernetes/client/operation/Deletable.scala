package com.goyeau.kubernetes.client.operation

import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.util.CirceEntityCodec._
import com.goyeau.kubernetes.client.util.EnrichedStatus
import io.k8s.apimachinery.pkg.apis.meta.v1.DeleteOptions
import org.http4s._
import org.http4s.Method._
import org.http4s.client.Client

private[client] trait Deletable[F[_]] {
  protected def httpClient: Client[F]
  implicit protected val F: Sync[F]
  protected def config: KubeConfig
  protected def resourceUri: Uri

  def delete(name: String, deleteOptions: Option[DeleteOptions] = None): F[Status] =
    httpClient
      .run(
        Request(
          DELETE,
          config.server.resolve(resourceUri) / name,
          headers = Headers(config.authorization.toList),
          body =
            deleteOptions.fold[EntityBody[F]](EmptyBody)(implicitly[EntityEncoder[F, DeleteOptions]].toEntity(_).body)
        )
      )
      .use(EnrichedStatus[F])
}
