package com.goyeau.kubernetesclient

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import io.circe._
import io.circe.generic.auto._
import io.k8s.api.apps.v1beta1.{Deployment, DeploymentList}

private[kubernetesclient] case class DeploymentsOperations(protected val config: KubeConfig)(
  implicit protected val system: ActorSystem,
  protected val decoder: Decoder[DeploymentList]
) extends Listable[DeploymentList] {
  protected val resourceUri = s"${config.server}/apis/extensions/v1beta1/deployments"

  def namespace(namespace: String) = NamespacedDeploymentsOperations(config, namespace)
}

private[kubernetesclient] case class NamespacedDeploymentsOperations(protected val config: KubeConfig,
                                                                     protected val namespace: String)(
  implicit protected val system: ActorSystem,
  protected val encoder: Encoder[Deployment],
  protected val decoder: Decoder[DeploymentList]
) extends Creatable[Deployment]
    with Listable[DeploymentList]
    with GroupDeletable {
  protected val resourceUri = s"${config.server}/apis/extensions/v1beta1/namespaces/$namespace/deployments"

  def apply(deploymentName: String) = DeploymentOperations(config, s"$resourceUri/$deploymentName")
}

private[kubernetesclient] case class DeploymentOperations(protected val config: KubeConfig,
                                                          protected val resourceUri: Uri)(
  implicit protected val system: ActorSystem,
  protected val encoder: Encoder[Deployment],
  protected val decoder: Decoder[Deployment]
) extends Gettable[Deployment]
    with Replaceable[Deployment]
    with Deletable
