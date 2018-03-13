package com.goyeau.kubernetesclient

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.reflectiveCalls
import scala.util.{Failure, Success}

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import io.circe.generic.auto._
import io.circe._
import io.circe.parser._
import io.k8s.apimachinery.pkg.apis.meta.v1.{DeleteOptions, ObjectMeta}
import com.goyeau.kubernetesclient.RequestUtils.nothingEncoder

trait Creatable[Resource <: { def metadata: Option[ObjectMeta] }] {
  protected def config: KubeConfig
  protected implicit val system: ActorSystem
  protected def resourceUri: Uri
  protected implicit def resourceEncoder: Encoder[Resource]

  def create(resource: Resource)(implicit ec: ExecutionContext): Future[Unit] =
    RequestUtils.singleRequest(config, HttpMethods.POST, resourceUri, data = Option(resource)).map(_ => ())

  def createOrUpdate(resource: Resource)(implicit ec: ExecutionContext): Future[Unit] = {
    val fullResourceUri: Uri = s"$resourceUri/${resource.metadata.get.name.get}"
    def update() =
      RequestUtils
        .singleRequest(config, HttpMethods.PATCH, fullResourceUri, RequestUtils.mergePatch, data = Option(resource))
        .map(_ => ())

    RequestUtils
      .singleRequest[Nothing](config, HttpMethods.GET, fullResourceUri)
      .flatMap(_ => update())
      .recoverWith {
        case KubernetesException(StatusCodes.NotFound.intValue, _, _) =>
          create(resource).recoverWith {
            case KubernetesException(StatusCodes.Conflict.intValue, _, _) => update()
          }
      }
  }
}

trait Replaceable[Resource <: { def metadata: Option[ObjectMeta] }] {
  protected def config: KubeConfig
  protected implicit val system: ActorSystem
  protected def resourceUri: Uri
  protected implicit def resourceEncoder: Encoder[Resource]

  def replace(resource: Resource)(implicit ec: ExecutionContext): Future[Unit] =
    RequestUtils
      .singleRequest(config,
                     HttpMethods.PUT,
                     s"$resourceUri/${resource.metadata.get.name.get}",
                     data = Option(resource))
      .map(_ => ())
}

trait Gettable[Resource] {
  protected def config: KubeConfig
  protected implicit val system: ActorSystem
  protected def resourceUri: Uri
  protected implicit def resourceDecoder: Decoder[Resource]

  def get(name: String)(implicit ec: ExecutionContext): Future[Resource] =
    RequestUtils
      .singleRequest(config, HttpMethods.GET, s"$resourceUri/$name")
      .map(response => decode[Resource](response).fold(throw _, identity))
}

trait Listable[Resource] {
  protected def config: KubeConfig
  protected implicit val system: ActorSystem
  protected def resourceUri: Uri
  protected implicit def listDecoder: Decoder[Resource]

  def list()(implicit ec: ExecutionContext): Future[Resource] =
    RequestUtils
      .singleRequest(config, HttpMethods.GET, resourceUri)
      .map(response => decode[Resource](response).fold(throw _, identity))
}

trait Proxy {
  protected def config: KubeConfig
  protected implicit val system: ActorSystem
  protected def resourceUri: Uri

  def proxy(
    name: String,
    method: HttpMethod,
    path: Uri,
    contentType: ContentType = ContentTypes.`text/plain(UTF-8)`,
    data: Option[String] = None
  )(implicit ec: ExecutionContext): Future[String] =
    RequestUtils.singleRequest(config, method, s"$resourceUri/$name/proxy$path", contentType, data)
}

trait Deletable {
  protected def config: KubeConfig
  protected implicit val system: ActorSystem
  protected def resourceUri: Uri

  def delete(name: String, deleteOptions: Option[DeleteOptions] = None)(implicit ec: ExecutionContext): Future[Unit] =
    RequestUtils.singleRequest(config, HttpMethods.DELETE, s"$resourceUri/$name", data = deleteOptions).map(_ => ())

  def deleteTerminated(name: String,
                       deleteOptions: Option[DeleteOptions] = None)(implicit ec: ExecutionContext): Future[Unit] = {
    def retry() = {
      Thread.sleep(1.second.toMillis)
      deleteTerminated(name, deleteOptions)
    }

    delete(name, deleteOptions).transformWith {
      case Success(_)                                                        => retry()
      case Failure(KubernetesException(StatusCodes.Conflict.intValue, _, _)) => retry()
      case Failure(KubernetesException(StatusCodes.NotFound.intValue, _, _)) => Future.unit
      case Failure(e)                                                        => Future.failed(e)
    }
  }
}

trait GroupDeletable {
  protected def config: KubeConfig
  protected implicit val system: ActorSystem
  protected def resourceUri: Uri

  def delete()(implicit ec: ExecutionContext): Future[Unit] =
    RequestUtils
      .singleRequest(config, HttpMethods.DELETE, resourceUri)
      .map(_ => ())
}
