package com.goyeau.kubernetesclient

import akka.actor.ActorSystem
import io.circe._
import io.k8s.api.apps.v1beta2.{Deployment, DeploymentList}

private[kubernetesclient] case class DeploymentsOperations(protected val config: KubeConfig)(
  implicit protected val system: ActorSystem,
  protected val listDecoder: Decoder[DeploymentList],
  encoder: Encoder[Deployment],
  decoder: Decoder[Deployment]
) extends Listable[DeploymentList] {
  protected val resourceUri = "apis/apps/v1beta2/deployments"

  def namespace(namespace: String) = NamespacedDeploymentsOperations(config, namespace)
}

private[kubernetesclient] case class NamespacedDeploymentsOperations(protected val config: KubeConfig,
                                                                     protected val namespace: String)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[Deployment],
  protected val resourceDecoder: Decoder[Deployment],
  protected val listDecoder: Decoder[DeploymentList]
) extends Creatable[Deployment]
    with Replaceable[Deployment]
    with Gettable[Deployment]
    with Listable[DeploymentList]
    with Deletable
    with GroupDeletable {
  protected val resourceUri = s"apis/apps/v1beta2/namespaces/$namespace/deployments"
}
