package com.goyeau.kubernetesclient

import akka.actor.ActorSystem
import io.circe._
import io.k8s.api.policy.v1beta1.{PodDisruptionBudget, PodDisruptionBudgetList}

private[kubernetesclient] case class PodDisruptionBudgetsOperations(protected val config: KubeConfig)(
  implicit protected val system: ActorSystem,
  protected val listDecoder: Decoder[PodDisruptionBudgetList],
  encoder: Encoder[PodDisruptionBudget],
  decoder: Decoder[PodDisruptionBudget]
) extends Listable[PodDisruptionBudgetList] {
  protected val resourceUri = "apis/policy/v1beta1/poddisruptionbudgets"

  def namespace(namespace: String) = NamespacedPodDisruptionBudgetOperations(config, namespace)
}

private[kubernetesclient] case class NamespacedPodDisruptionBudgetOperations(protected val config: KubeConfig,
                                                                             protected val namespace: String)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[PodDisruptionBudget],
  protected val resourceDecoder: Decoder[PodDisruptionBudget],
  protected val listDecoder: Decoder[PodDisruptionBudgetList]
) extends Creatable[PodDisruptionBudget]
    with Replaceable[PodDisruptionBudget]
    with Gettable[PodDisruptionBudget]
    with Listable[PodDisruptionBudgetList]
    with Deletable
    with GroupDeletable {
  protected val resourceUri = s"apis/policy/v1beta1/namespaces/$namespace/poddisruptionbudgets"
}
