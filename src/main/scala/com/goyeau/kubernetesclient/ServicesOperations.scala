package com.goyeau.kubernetesclient

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import io.circe._
import io.k8s.api.core.v1.{Service, ServiceList}

private[kubernetesclient] case class ServicesOperations(protected val config: KubeConfig)(
  implicit protected val system: ActorSystem,
  protected val resourceDecoder: Decoder[ServiceList],
  encoder: Encoder[Service],
  decoder: Decoder[Service]
) extends Listable[ServiceList] {
  protected val resourceUri = s"${config.server}/api/v1/services"

  def namespace(namespace: String) = NamespacedServicesOperations(config, namespace)
}

private[kubernetesclient] case class NamespacedServicesOperations(protected val config: KubeConfig,
                                                                  protected val namespace: String)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[Service],
  decoder: Decoder[Service],
  protected val resourceDecoder: Decoder[ServiceList]
) extends Creatable[Service]
    with Listable[ServiceList]
    with GroupDeletable {
  protected val resourceUri = s"${config.server}/api/v1/namespaces/$namespace/services"

  def apply(serviceName: String) = ServiceOperations(config, s"$resourceUri/$serviceName")
}

private[kubernetesclient] case class ServiceOperations(protected val config: KubeConfig,
                                                       protected val resourceUri: Uri)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[Service],
  protected val resourceDecoder: Decoder[Service]
) extends Gettable[Service]
    with Replaceable[Service]
    with Deletable
