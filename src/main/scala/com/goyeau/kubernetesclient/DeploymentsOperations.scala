package com.goyeau.kubernetesclient

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import io.circe._
import io.circe.generic.auto._
import io.k8s.api.apps.v1beta1.Deployment

private[kubernetesclient] case class DeploymentsOperations(config: KubeConfig, private val namespace: String)(
  implicit val system: ActorSystem,
  val encoder: Encoder[Deployment]
) extends Creatable[Deployment]
    with GroupDeletable {
  val resourceUri = s"${config.server}/apis/extensions/v1beta1/namespaces/$namespace/deployments"

  def apply(deploymentName: String) = DeploymentOperations(config, s"$resourceUri/$deploymentName")
}

private[kubernetesclient] case class DeploymentOperations(config: KubeConfig, resourceUri: Uri)(
  implicit val system: ActorSystem,
  val decoder: Decoder[Deployment]
) extends Gettable[Deployment]
    with Deletable
