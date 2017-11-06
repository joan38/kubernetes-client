package com.goyeau.kubernetesclient

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import io.circe._
import io.k8s.api.core.v1.{ConfigMap, ConfigMapList}

private[kubernetesclient] case class ConfigMapsOperations(protected val config: KubeConfig)(
  implicit protected val system: ActorSystem,
  protected val resourceDecoder: Decoder[ConfigMapList],
  encoder: Encoder[ConfigMap],
  decoder: Decoder[ConfigMap]
) extends Listable[ConfigMapList] {
  protected val resourceUri = s"${config.server}/api/v1/configmaps"

  def namespace(namespace: String) = NamespacedConfigMapsOperations(config, namespace)
}

private[kubernetesclient] case class NamespacedConfigMapsOperations(protected val config: KubeConfig,
                                                                    protected val namespace: String)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[ConfigMap],
  decoder: Decoder[ConfigMap],
  protected val resourceDecoder: Decoder[ConfigMapList]
) extends Creatable[ConfigMap]
    with Listable[ConfigMapList]
    with GroupDeletable {
  protected val resourceUri = s"${config.server}/api/v1/namespaces/$namespace/configmaps"

  def apply(configMapName: String) = ConfigMapOperations(config, s"$resourceUri/$configMapName")
}

private[kubernetesclient] case class ConfigMapOperations(protected val config: KubeConfig,
                                                         protected val resourceUri: Uri)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[ConfigMap],
  protected val resourceDecoder: Decoder[ConfigMap]
) extends Gettable[ConfigMap]
    with Replaceable[ConfigMap]
    with Deletable
