package com.goyeau.kubernetes.client.operation

import scala.language.reflectiveCalls
import cats.implicits.*
import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.util.CirceEntityCodec.*
import com.goyeau.kubernetes.client.util.{CachedExecToken, EnrichedStatus}
import io.circe.*
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s.*
import org.http4s.client.Client
import org.http4s.client.UnexpectedStatus
import org.http4s.headers.`Content-Type`
import org.http4s.Method.*

private[client] trait Creatable[F[_], Resource <: { def metadata: Option[ObjectMeta] }] {
  protected def httpClient: Client[F]
  implicit protected val F: Async[F]
  protected def config: KubeConfig
  protected def resourceUri: Uri
  protected def cachedExecToken: Option[CachedExecToken[F]]
  implicit protected def resourceEncoder: Encoder[Resource]
  implicit protected def resourceDecoder: Decoder[Resource]

  def create(resource: Resource): F[Status] =
    httpClient.runF(buildRequest(resource)).use(EnrichedStatus[F])

  def createWithResource(resource: Resource): F[Resource] =
    httpClient.expect[Resource](buildRequest(resource))

  private def buildRequest(resource: Resource) =
    Request[F](POST, config.server.resolve(resourceUri))
      .withEntity(resource)
      .withOptionalAuthorization(config.authorization, cachedExecToken)

  def createOrUpdate(resource: Resource): F[Status] = {
    val fullResourceUri = config.server.resolve(resourceUri) / resource.metadata.get.name.get
    def update          = httpClient.runF(buildRequest(resource, fullResourceUri)).use(EnrichedStatus[F])

    httpClient
      .runF(Request[F](GET, fullResourceUri).withOptionalAuthorization(config.authorization, cachedExecToken))
      .use(EnrichedStatus.apply[F])
      .flatMap {
        case status if status.isSuccess => update
        case Status.NotFound =>
          create(resource).flatMap {
            case Status.Conflict => update
            case status          => F.pure(status)
          }
        case status => F.pure(status)
      }
  }

  def createOrUpdateWithResource(resource: Resource): F[Resource] = {
    val fullResourceUri    = config.server.resolve(resourceUri) / resource.metadata.get.name.get
    def updateWithResource = httpClient.expect[Resource](buildRequest(resource, fullResourceUri))

    httpClient
      .expectOptionF[Resource](
        Request[F](GET, fullResourceUri).withOptionalAuthorization(config.authorization, cachedExecToken)
      )
      .flatMap {
        case Some(_) => updateWithResource
        case None =>
          createWithResource(resource).recoverWith {
            case UnexpectedStatus(status, _, _) if status === Status.Conflict => updateWithResource
          }
      }
  }

  private def buildRequest(resource: Resource, fullResourceUri: Uri) =
    Request[F](PATCH, fullResourceUri)
      .withEntity(resource)
      .putHeaders(`Content-Type`(MediaType.application.`merge-patch+json`))
      .withOptionalAuthorization(config.authorization, cachedExecToken)
}
