package com.goyeau.kubernetesclient

import akka.actor.ActorSystem
import io.circe._
import io.k8s.api.core.v1.{Service, ServiceList}

private[kubernetesclient] case class ServicesOperations(protected val config: KubeConfig)(
  implicit protected val system: ActorSystem,
  protected val listDecoder: Decoder[ServiceList],
  encoder: Encoder[Service],
  decoder: Decoder[Service]
) extends Listable[ServiceList] {
  protected val resourceUri = "api/v1/services"

  def namespace(namespace: String) = NamespacedServicesOperations(config, namespace)
}

private[kubernetesclient] case class NamespacedServicesOperations(
  protected val config: KubeConfig,
  protected val namespace: String
)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[Service],
  protected val resourceDecoder: Decoder[Service],
  protected val listDecoder: Decoder[ServiceList]
) extends Creatable[Service]
    with Replaceable[Service]
    with Gettable[Service]
    with Listable[ServiceList]
    with Proxy
    with Deletable
    with GroupDeletable {
  protected val resourceUri = s"api/v1/namespaces/$namespace/services"
}
