package com.goyeau.kubernetes.client.operation

import scala.language.reflectiveCalls
import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.util.CirceEntityCodec.*
import io.circe.*
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s.*
import org.http4s.client.Client
import org.http4s.Method.*
import org.http4s.headers.Authorization

private[client] trait Replaceable[F[_], Resource <: { def metadata: Option[ObjectMeta] }] {
  protected def httpClient: Client[F]
  implicit protected val F: Async[F]
  protected def config: KubeConfig[F]
  protected def authorization: Option[F[Authorization]]
  protected def resourceUri: Uri
  implicit protected def resourceEncoder: Encoder[Resource]
  implicit protected def resourceDecoder: Decoder[Resource]

  def replace(resource: Resource): F[Status] =
    httpClient.status(buildRequest(resource))

  def replaceWithResource(resource: Resource): F[Resource] =
    httpClient.expect[Resource](buildRequest(resource))

  private def buildRequest(resource: Resource) =
    Request[F](PUT, config.server.resolve(resourceUri) / resource.metadata.get.name.get)
      .withEntity(resource)
      .withOptionalAuthorization(authorization)
}
