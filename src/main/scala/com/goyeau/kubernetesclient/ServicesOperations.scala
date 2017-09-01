package com.goyeau.kubernetesclient

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import io.circe._
import io.circe.generic.auto._
import io.k8s.api.core.v1.Service

private[kubernetesclient] case class ServicesOperations(config: KubeConfig, private val namespace: String)(
  implicit val system: ActorSystem,
  val encoder: Encoder[Service]
) extends Creatable[Service]
    with GroupDeletable {
  val resourceUri = s"${config.server}/api/v1/namespaces/$namespace/services"

  def apply(serviceName: String) = ServiceOperations(config, s"$resourceUri/$serviceName")
}

private[kubernetesclient] case class ServiceOperations(config: KubeConfig, resourceUri: Uri)(
  implicit val system: ActorSystem,
  val decoder: Decoder[Service]
) extends Gettable[Service]
    with Deletable
