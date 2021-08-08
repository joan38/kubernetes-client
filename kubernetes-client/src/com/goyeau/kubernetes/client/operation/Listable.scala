package com.goyeau.kubernetes.client.operation

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.util.CirceEntityCodec._
import com.goyeau.kubernetes.client.util.Uris.addLabels
import io.circe._
import org.http4s._
import org.http4s.client.Client
import org.http4s.Method._

private[client] trait Listable[F[_], Resource] {
  protected def httpClient: Client[F]
  implicit protected val F: Async[F]
  protected def config: KubeConfig
  protected def resourceUri: Uri
  implicit protected def listDecoder: Decoder[Resource]

  def list(labels: Map[String, String] = Map.empty): F[Resource] = {
    val uri = addLabels(labels, config.server.resolve(resourceUri))
    httpClient.expect[Resource](Request[F](GET, uri).withOptionalAuthorization(config.authorization))
  }
}
