package com.goyeau.kubernetesclient

import akka.actor.ActorSystem
import io.circe._
import io.k8s.api.apps.v1beta2.{StatefulSet, StatefulSetList}

private[kubernetesclient] case class StatefulSetsOperations(protected val config: KubeConfig)(
  implicit protected val system: ActorSystem,
  protected val listDecoder: Decoder[StatefulSetList],
  encoder: Encoder[StatefulSet],
  decoder: Decoder[StatefulSet]
) extends Listable[StatefulSetList] {
  protected val resourceUri = "apis/apps/v1beta2/statefulsets"

  def namespace(namespace: String) = NamespacedStatefulSetsOperations(config, namespace)
}

private[kubernetesclient] case class NamespacedStatefulSetsOperations(
  protected val config: KubeConfig,
  protected val namespace: String
)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[StatefulSet],
  protected val resourceDecoder: Decoder[StatefulSet],
  protected val listDecoder: Decoder[StatefulSetList]
) extends Creatable[StatefulSet]
    with Replaceable[StatefulSet]
    with Gettable[StatefulSet]
    with Listable[StatefulSetList]
    with Deletable
    with GroupDeletable {
  protected val resourceUri = s"apis/apps/v1beta2/namespaces/$namespace/statefulsets"
}
