package com.goyeau.kubernetesclient

import scala.concurrent.{ExecutionContext, Future}
import scala.language.reflectiveCalls

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpMethods, StatusCodes, Uri}
import io.circe.generic.auto._
import io.circe._
import io.circe.parser._
import io.k8s.apimachinery.pkg.apis.meta.v1.{DeleteOptions, ObjectMeta}
import com.goyeau.kubernetesclient.RequestUtils.nothingEncoder

trait Creatable[Resource] {
  protected def config: KubeConfig
  protected def resourceUri: Uri
  protected implicit def resourceEncoder: Encoder[Resource]

  def create(resource: Resource)(implicit system: ActorSystem): Future[Unit] = {
    implicit val ec: ExecutionContext = system.dispatcher
    RequestUtils
      .singleRequest(config, HttpMethods.POST, resourceUri, Option(resource))
      .map(_ => ())
  }
}

trait Replaceable[Resource] {
  protected def config: KubeConfig
  protected def resourceUri: Uri
  protected implicit def resourceEncoder: Encoder[Resource]

  def replace(resource: Resource)(implicit system: ActorSystem): Future[Unit] = {
    implicit val ec: ExecutionContext = system.dispatcher
    RequestUtils
      .singleRequest(config, HttpMethods.PUT, resourceUri, Option(resource))
      .map(_ => ())
  }
}

trait CreateOrUpdatable[Resource <: { def metadata: Option[ObjectMeta] }] {
  self: Creatable[Resource] =>
  protected def config: KubeConfig
  protected def resourceUri: Uri
  protected implicit def resourceEncoder: Encoder[Resource]

  def createOrUpdate(resource: Resource)(implicit system: ActorSystem): Future[Unit] = {
    implicit val ec: ExecutionContext = system.dispatcher

    val fullResourceUri: Uri = s"$resourceUri/${resource.metadata.get.name.get}"
    RequestUtils
      .singleRequest[Nothing](config, HttpMethods.GET, fullResourceUri)
      .recoverWith {
        case e: KubernetesException if e.statusCode == StatusCodes.NotFound.intValue => create(resource)
      }
      .flatMap { _ =>
        RequestUtils
          .singleRequest(config,
                         HttpMethods.PATCH,
                         fullResourceUri,
                         Option(resource),
                         RequestUtils.strategicMergePatch)
          .map(_ => ())
      }
  }
}

trait Gettable[Resource] {
  protected def config: KubeConfig
  protected def resourceUri: Uri
  protected implicit def resourceDecoder: Decoder[Resource]

  def get()(implicit system: ActorSystem): Future[Resource] = {
    implicit val ec: ExecutionContext = system.dispatcher
    RequestUtils
      .singleRequest(config, HttpMethods.GET, resourceUri)
      .map(response => decode[Resource](response).fold(throw _, identity))
  }
}

trait Listable[Resource] {
  protected def config: KubeConfig
  protected def resourceUri: Uri
  protected implicit def resourceDecoder: Decoder[Resource]

  def list()(implicit system: ActorSystem): Future[Resource] = {
    implicit val ec: ExecutionContext = system.dispatcher
    RequestUtils
      .singleRequest(config, HttpMethods.GET, resourceUri)
      .map(response => decode[Resource](response).fold(throw _, identity))
  }
}

trait Deletable {
  protected def config: KubeConfig
  protected def resourceUri: Uri

  def delete(deleteOptions: Option[DeleteOptions] = None)(implicit system: ActorSystem): Future[Unit] = {
    implicit val ec: ExecutionContext = system.dispatcher
    RequestUtils
      .singleRequest(config, HttpMethods.DELETE, resourceUri, deleteOptions)
      .map(_ => ())
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
