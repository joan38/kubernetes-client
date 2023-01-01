package com.goyeau.kubernetes.client.operation

import scala.language.reflectiveCalls
import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.util.CirceEntityCodec._
import com.goyeau.kubernetes.client.util.CachedExecToken
import io.circe._
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s._
import org.http4s.client.Client
import org.http4s.Method._

private[client] trait Replaceable[F[_], Resource <: { def metadata: Option[ObjectMeta] }] {
  protected def httpClient: Client[F]
  implicit protected val F: Async[F]
  protected def config: KubeConfig[F]
  protected def cachedExecToken: Option[CachedExecToken[F]]
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
      .withOptionalAuthorization(config.authorization, cachedExecToken)
}
