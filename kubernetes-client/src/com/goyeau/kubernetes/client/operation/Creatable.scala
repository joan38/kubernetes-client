package com.goyeau.kubernetes.client.operation

import scala.language.reflectiveCalls
import cats.implicits._
import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.util.CirceEntityCodec._
import com.goyeau.kubernetes.client.util.EnrichedStatus
import io.circe._
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.UnexpectedStatus
import org.http4s.headers.`Content-Type`
import org.http4s.Method._

private[client] trait Creatable[F[_], Resource <: { def metadata: Option[ObjectMeta] }] {
  protected def httpClient: Client[F]
  implicit protected val F: Async[F]
  protected def config: KubeConfig
  protected def resourceUri: Uri
  implicit protected def resourceEncoder: Encoder[Resource]
  implicit protected def resourceDecoder: Decoder[Resource]

  def create(resource: Resource): F[Status] =
    httpClient.run(buildRequest(resource)).use(EnrichedStatus[F])

  def createWithResource(resource: Resource): F[Resource] =
    httpClient.expect[Resource](buildRequest(resource))

  private def buildRequest(resource: Resource) =
    Request[F](POST, config.server.resolve(resourceUri))
      .withEntity(resource)
      .withOptionalAuthorization(config.authorization)

  def createOrUpdate(resource: Resource): F[Status] = {
    val fullResourceUri = config.server.resolve(resourceUri) / resource.metadata.get.name.get
    def update          = httpClient.run(buildRequest(resource, fullResourceUri)).use(EnrichedStatus[F])

    httpClient
      .run(Request[F](GET, fullResourceUri).withOptionalAuthorization(config.authorization))
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
      .expectOption[Resource](Request[F](GET, fullResourceUri).withOptionalAuthorization(config.authorization))
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
      .withOptionalAuthorization(config.authorization)
}
