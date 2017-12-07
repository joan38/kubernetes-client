package com.goyeau.kubernetesclient

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import io.circe._
import io.k8s.api.apps.v1beta2.{StatefulSet, StatefulSetList}

private[kubernetesclient] case class StatefulSetsOperations(protected val config: KubeConfig)(
  implicit protected val system: ActorSystem,
  protected val resourceDecoder: Decoder[StatefulSetList],
  encoder: Encoder[StatefulSet],
  decoder: Decoder[StatefulSet]
) extends Listable[StatefulSetList] {
  protected val resourceUri = s"${config.server}/apis/apps/v1beta2/statefulsets"

  def namespace(namespace: String) = NamespacedStatefulSetsOperations(config, namespace)
}

private[kubernetesclient] case class NamespacedStatefulSetsOperations(protected val config: KubeConfig,
                                                                      protected val namespace: String)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[StatefulSet],
  decoder: Decoder[StatefulSet],
  protected val resourceDecoder: Decoder[StatefulSetList]
) extends Creatable[StatefulSet]
    with CreateOrUpdatable[StatefulSet]
    with Listable[StatefulSetList]
    with GroupDeletable {
  protected val resourceUri = s"${config.server}/apis/apps/v1beta2/namespaces/$namespace/statefulsets"

  def apply(statefulSetName: String) = StatefulSetOperations(config, s"$resourceUri/$statefulSetName")
}

private[kubernetesclient] case class StatefulSetOperations(protected val config: KubeConfig,
                                                           protected val resourceUri: Uri)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[StatefulSet],
  protected val resourceDecoder: Decoder[StatefulSet]
) extends Gettable[StatefulSet]
    with Replaceable[StatefulSet]
    with Deletable
