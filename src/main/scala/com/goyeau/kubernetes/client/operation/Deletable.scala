package com.goyeau.kubernetes.client.operation

import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.util.CirceEntityCodec._
import com.goyeau.kubernetes.client.util.EnrichedStatus
import com.goyeau.kubernetes.client.util.Uris.addLabels
import io.k8s.apimachinery.pkg.apis.meta.v1.DeleteOptions
import org.http4s.Method._
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl

private[client] trait Deletable[F[_]] extends Http4sClientDsl[F] {
  protected def httpClient: Client[F]
  implicit protected val F: Sync[F]
  protected def config: KubeConfig
  protected def resourceUri: Uri
  protected val labels: Map[String, String]

  def delete(
      name: String,
      deleteOptions: Option[DeleteOptions] = None
  ): F[Status] = {
    val uri = addLabels(labels, config.server.resolve(resourceUri) / name)
    sendDelete(deleteOptions, uri)
  }

  private def sendDelete(deleteOptions: Option[DeleteOptions], uri: Uri) =
    httpClient.fetch(
      Request(
        DELETE,
        uri,
        headers = Headers(config.authorization.toList),
        body =
          deleteOptions.fold[EntityBody[F]](EmptyBody)(implicitly[EntityEncoder[F, DeleteOptions]].toEntity(_).body)
      )
    )(EnrichedStatus[F])
}
