package com.goyeau.kubernetes.client

import akka.actor.ActorSystem
import io.circe._
import io.k8s.api.core.v1.{ConfigMap, ConfigMapList}

private[client] case class ConfigMapsOperations(protected val config: KubeConfig)(
  implicit protected val system: ActorSystem,
  protected val listDecoder: Decoder[ConfigMapList],
  encoder: Encoder[ConfigMap],
  decoder: Decoder[ConfigMap]
) extends Listable[ConfigMapList] {
  protected val resourceUri = "api/v1/configmaps"

  def namespace(namespace: String) = NamespacedConfigMapsOperations(config, namespace)
}

private[client] case class NamespacedConfigMapsOperations(
  protected val config: KubeConfig,
  protected val namespace: String
)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[ConfigMap],
  protected val resourceDecoder: Decoder[ConfigMap],
  protected val listDecoder: Decoder[ConfigMapList]
) extends Creatable[ConfigMap]
    with Replaceable[ConfigMap]
    with Gettable[ConfigMap]
    with Listable[ConfigMapList]
    with Deletable
    with GroupDeletable {
  protected val resourceUri = s"api/v1/namespaces/$namespace/configmaps"
}
