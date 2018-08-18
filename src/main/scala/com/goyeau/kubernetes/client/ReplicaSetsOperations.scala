package com.goyeau.kubernetes.client

import akka.actor.ActorSystem
import io.circe._
import io.k8s.api.apps.v1beta2.{ReplicaSet, ReplicaSetList}

private[client] case class ReplicaSetsOperations(protected val config: KubeConfig)(
  implicit protected val system: ActorSystem,
  protected val listDecoder: Decoder[ReplicaSetList],
  encoder: Encoder[ReplicaSet],
  decoder: Decoder[ReplicaSet]
) extends Listable[ReplicaSetList] {
  protected val resourceUri = "apis/apps/v1beta2/replicasets"

  def namespace(namespace: String) = NamespacedReplicaSetsOperations(config, namespace)
}

private[client] case class NamespacedReplicaSetsOperations(
  protected val config: KubeConfig,
  protected val namespace: String
)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[ReplicaSet],
  protected val resourceDecoder: Decoder[ReplicaSet],
  protected val listDecoder: Decoder[ReplicaSetList]
) extends Creatable[ReplicaSet]
    with Replaceable[ReplicaSet]
    with Gettable[ReplicaSet]
    with Listable[ReplicaSetList]
    with Deletable
    with GroupDeletable {
  protected val resourceUri = s"apis/apps/v1beta2/namespaces/$namespace/replicasets"
}
