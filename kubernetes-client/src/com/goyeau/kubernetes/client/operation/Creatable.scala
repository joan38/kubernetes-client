package com.goyeau.kubernetes.client.operation

import scala.language.reflectiveCalls
import cats.implicits._
import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.util.CirceEntityCodec._
import com.goyeau.kubernetes.client.util.EnrichedStatus
import io.circe._
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s._
import org.http4s.client.Client
import org.http4s.headers.`Content-Type`
import org.http4s.Method._

private[client] trait Creatable[F[_], Resource <: { def metadata: Option[ObjectMeta] }] {
  protected def httpClient: Client[F]
  implicit protected val F: Sync[F]
  protected def config: KubeConfig
  protected def resourceUri: Uri
  implicit protected def resourceEncoder: Encoder[Resource]

  def create(resource: Resource): F[Status] =
    httpClient.run(Request[F](POST, config.server.resolve(resourceUri)).withEntity(resource).putHeaders(config.authorization.toSeq: _*)).use(
      EnrichedStatus[F]
    )

  def createOrUpdate(resource: Resource): F[Status] = {
    val fullResourceUri = config.server.resolve(resourceUri) / resource.metadata.get.name.get
    def update =
      httpClient.run(
        Request[F](PATCH, fullResourceUri).withEntity(resource)
          .putHeaders(
          `Content-Type`(MediaType.application.`merge-patch+json`) +: config.authorization.toSeq: _*
        )
      ).use(EnrichedStatus[F])

    httpClient
      .run(Request[F](GET, fullResourceUri).putHeaders(config.authorization.toSeq: _*)).use(EnrichedStatus.apply[F])
      .flatMap {
        case status if status.isSuccess => update
        case Status.NotFound =>
          create(resource).recoverWith {
            case Status.Conflict => update
          }
        case status => F.pure(status)
      }
  }
}
