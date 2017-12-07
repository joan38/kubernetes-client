package com.goyeau.kubernetesclient

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import io.circe._
import io.k8s.api.policy.v1beta1.{PodDisruptionBudget, PodDisruptionBudgetList}

private[kubernetesclient] case class PodDisruptionBudgetsOperations(protected val config: KubeConfig)(
  implicit protected val system: ActorSystem,
  protected val resourceDecoder: Decoder[PodDisruptionBudgetList],
  encoder: Encoder[PodDisruptionBudget],
  decoder: Decoder[PodDisruptionBudget]
) extends Listable[PodDisruptionBudgetList] {
  protected val resourceUri = s"${config.server}/apis/policy/v1beta1/poddisruptionbudgets"

  def namespace(namespace: String) = NamespacedPodDisruptionBudgetOperations(config, namespace)
}

private[kubernetesclient] case class NamespacedPodDisruptionBudgetOperations(protected val config: KubeConfig,
                                                                             protected val namespace: String)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[PodDisruptionBudget],
  decoder: Decoder[PodDisruptionBudget],
  protected val resourceDecoder: Decoder[PodDisruptionBudgetList]
) extends Creatable[PodDisruptionBudget]
    with CreateOrUpdatable[PodDisruptionBudget]
    with Listable[PodDisruptionBudgetList]
    with GroupDeletable {
  protected val resourceUri = s"${config.server}/apis/policy/v1beta1/namespaces/$namespace/poddisruptionbudgets"

  def apply(podDisruptionBudgetName: String) =
    PodDisruptionBudgetOperations(config, s"$resourceUri/$podDisruptionBudgetName")
}

private[kubernetesclient] case class PodDisruptionBudgetOperations(protected val config: KubeConfig,
                                                                   protected val resourceUri: Uri)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[PodDisruptionBudget],
  protected val resourceDecoder: Decoder[PodDisruptionBudget]
) extends Gettable[PodDisruptionBudget]
    with Replaceable[PodDisruptionBudget]
    with Deletable
