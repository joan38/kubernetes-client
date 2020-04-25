package com.goyeau.kubernetes.client.operation

import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.util.CirceEntityCodec._
import com.goyeau.kubernetes.client.util.Uris.addLabels
import io.circe._
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.Method._

private[client] trait Listable[F[_], Resource] extends Http4sClientDsl[F] {
  protected def httpClient: Client[F]
  implicit protected val F: Sync[F]
  protected def config: KubeConfig
  protected def resourceUri: Uri
  implicit protected def listDecoder: Decoder[Resource]

  @deprecated("Use list() instead", "0.4.0")
  def list: F[Resource] = list()

  def list(labels: Map[String, String] = Map.empty): F[Resource] = {
    val uri = addLabels(labels, config.server.resolve(resourceUri))
    httpClient.expect[Resource](GET(uri, config.authorization.toSeq: _*))
  }
}
