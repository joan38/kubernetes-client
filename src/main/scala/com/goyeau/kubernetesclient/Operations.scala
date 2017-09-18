package com.goyeau.kubernetesclient

import scala.concurrent.{ExecutionContext, Future}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpMethods, Uri}
import akka.stream.Materializer
import io.circe._
import io.circe.parser._
import io.circe.generic.auto._
import io.k8s.apimachinery.pkg.apis.meta.v1.DeleteOptions
import com.goyeau.kubernetesclient.RequestUtils.nothingEncoder

trait Creatable[Resource] {
  protected def config: KubeConfig
  protected def resourceUri: Uri
  protected implicit def system: ActorSystem
  protected implicit def encoder: Encoder[Resource]

  def create(resource: Resource)(implicit ec: ExecutionContext, mat: Materializer): Future[Unit] =
    RequestUtils
      .singleRequest(config, HttpMethods.POST, resourceUri, Option(resource))
      .map(_ => ())
}

trait Replaceable[Resource] {
  protected def config: KubeConfig
  protected def resourceUri: Uri
  protected implicit def system: ActorSystem
  protected implicit def encoder: Encoder[Resource]

  def replace(resource: Resource)(implicit ec: ExecutionContext, mat: Materializer): Future[Unit] =
    RequestUtils
      .singleRequest(config, HttpMethods.PUT, resourceUri, Option(resource))
      .map(_ => ())
}

trait Gettable[Resource] {
  protected def config: KubeConfig
  protected def resourceUri: Uri
  protected implicit def system: ActorSystem
  protected implicit def decoder: Decoder[Resource]

  def get()(implicit ec: ExecutionContext, mat: Materializer): Future[Resource] =
    RequestUtils
      .singleRequest(config, HttpMethods.GET, resourceUri)
      .map(response => decode[Resource](response).fold(throw _, identity))
}

trait Deletable {
  protected def config: KubeConfig
  protected def resourceUri: Uri
  protected implicit def system: ActorSystem

  def delete(deleteOptions: Option[DeleteOptions] = None)(implicit ec: ExecutionContext,
                                                          mat: Materializer): Future[Unit] =
    RequestUtils
      .singleRequest(config, HttpMethods.DELETE, resourceUri, deleteOptions)
      .map(_ => ())
}

trait GroupDeletable {
  protected def config: KubeConfig
  protected def resourceUri: Uri
  protected implicit def system: ActorSystem

  def delete()(implicit ec: ExecutionContext, mat: Materializer): Future[Unit] =
    RequestUtils
      .singleRequest(config, HttpMethods.DELETE, resourceUri)
      .map(_ => ())
}
