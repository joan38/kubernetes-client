package com.goyeau.kubernetesclient

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import io.circe._
import io.circe.generic.auto._
import io.k8s.api.core.v1.{Service, ServiceList}

private[kubernetesclient] case class ServicesOperations(protected val config: KubeConfig)(
  implicit protected val system: ActorSystem,
  protected val decoder: Decoder[ServiceList]
) extends Listable[ServiceList] {
  protected val resourceUri = s"${config.server}/api/v1/services"

  def namespace(namespace: String) = NamespacedServicesOperations(config, namespace)
}

private[kubernetesclient] case class NamespacedServicesOperations(protected val config: KubeConfig,
                                                                  protected val namespace: String)(
  implicit protected val system: ActorSystem,
  protected val encoder: Encoder[Service],
  protected val decoder: Decoder[ServiceList]
) extends Creatable[Service]
    with Listable[ServiceList]
    with GroupDeletable {
  protected val resourceUri = s"${config.server}/api/v1/namespaces/$namespace/services"

  def apply(serviceName: String) = ServiceOperations(config, s"$resourceUri/$serviceName")
}

private[kubernetesclient] case class ServiceOperations(protected val config: KubeConfig,
                                                       protected val resourceUri: Uri)(
  implicit protected val system: ActorSystem,
  protected val encoder: Encoder[Service],
  protected val decoder: Decoder[Service]
) extends Gettable[Service]
    with Replaceable[Service]
    with Deletable
