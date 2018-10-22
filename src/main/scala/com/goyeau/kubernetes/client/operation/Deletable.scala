package com.goyeau.kubernetes.client.operation

import cats.effect.{Sync, Timer}
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.util.CirceEntityCodec._
import com.goyeau.kubernetes.client.util.EnrichedStatus
import io.k8s.apimachinery.pkg.apis.meta.v1.DeleteOptions
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.Method._

private[client] trait Deletable[F[_]] extends Http4sClientDsl[F] {
  protected def httpClient: Client[F]
  implicit protected val F: Sync[F]
  implicit protected val timer: Timer[F]
  protected def config: KubeConfig
  protected def resourceUri: Uri

  def delete(name: String, deleteOptions: Option[DeleteOptions] = None): F[Status] =
    httpClient.fetch(
      Request(
        DELETE,
        config.server.resolve(resourceUri) / name,
        headers = Headers(config.authorization.toList),
        body =
          deleteOptions.fold[EntityBody[F]](EmptyBody)(implicitly[EntityEncoder[F, DeleteOptions]].toEntity(_).body)
      )
    )(EnrichedStatus[F])
}
