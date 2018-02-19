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
  protected def resourceUri: Uri
  protected implicit def resourceEncoder: Encoder[Resource]

  def create(resource: Resource)(implicit system: ActorSystem): Future[Unit] = {
    implicit val ec: ExecutionContext = system.dispatcher
    RequestUtils.singleRequest(config, HttpMethods.POST, resourceUri, Option(resource)).map(_ => ())
  }

  def createOrUpdate(resource: Resource)(implicit system: ActorSystem): Future[Unit] = {
    implicit val ec: ExecutionContext = system.dispatcher

    val fullResourceUri: Uri = s"$resourceUri/${resource.metadata.get.name.get}"
    def update() =
      RequestUtils
        .singleRequest(config, HttpMethods.PATCH, fullResourceUri, Option(resource), RequestUtils.mergePatch)
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
  protected def resourceUri: Uri
  protected implicit def resourceEncoder: Encoder[Resource]

  def replace(resource: Resource)(implicit system: ActorSystem): Future[Unit] = {
    implicit val ec: ExecutionContext = system.dispatcher
    RequestUtils
      .singleRequest(config, HttpMethods.PUT, s"$resourceUri/${resource.metadata.get.name.get}", Option(resource))
      .map(_ => ())
  }
}

trait Gettable[Resource] {
  protected def config: KubeConfig
  protected def resourceUri: Uri
  protected implicit def resourceDecoder: Decoder[Resource]

  def get(name: String)(implicit system: ActorSystem): Future[Resource] = {
    implicit val ec: ExecutionContext = system.dispatcher
    RequestUtils
      .singleRequest(config, HttpMethods.GET, s"$resourceUri/$name")
      .map(response => decode[Resource](response).fold(throw _, identity))
  }
}

trait Listable[Resource] {
  protected def config: KubeConfig
  protected def resourceUri: Uri
  protected implicit def listDecoder: Decoder[Resource]

  def list()(implicit system: ActorSystem): Future[Resource] = {
    implicit val ec: ExecutionContext = system.dispatcher
    RequestUtils
      .singleRequest(config, HttpMethods.GET, resourceUri)
      .map(response => decode[Resource](response).fold(throw _, identity))
  }
}

trait Proxy {
  protected def config: KubeConfig
  protected def resourceUri: Uri

  def proxy(
    name: String,
    method: HttpMethod,
    path: Uri,
    data: Option[String] = None,
    contentType: ContentType = ContentTypes.`text/plain(UTF-8)`
  )(implicit system: ActorSystem): Future[String] = {
    implicit val ec: ExecutionContext = system.dispatcher
    RequestUtils.singleRequest(config, method, s"$resourceUri/$name/proxy/$path", data, contentType)
  }
}

trait Deletable {
  protected def config: KubeConfig
  protected def resourceUri: Uri

  def delete(name: String, deleteOptions: Option[DeleteOptions] = None)(implicit system: ActorSystem): Future[Unit] = {
    implicit val ec: ExecutionContext = system.dispatcher
    RequestUtils.singleRequest(config, HttpMethods.DELETE, s"$resourceUri/$name", deleteOptions).map(_ => ())
  }

  def deleteTerminated(name: String,
                       deleteOptions: Option[DeleteOptions] = None)(implicit system: ActorSystem): Future[Unit] = {
    implicit val ec: ExecutionContext = system.dispatcher

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
  protected def resourceUri: Uri

  def delete()(implicit system: ActorSystem): Future[Unit] = {
    implicit val ec: ExecutionContext = system.dispatcher
    RequestUtils
      .singleRequest(config, HttpMethods.DELETE, resourceUri)
      .map(_ => ())
  }
}
