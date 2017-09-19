package com.goyeau.kubernetesclient

import akka.actor.ActorSystem
import io.circe._
import io.circe.generic.auto._
import io.k8s.api.core.v1.Namespace

case class KubernetesClient(config: KubeConfig)(implicit system: ActorSystem) {
  lazy val namespaces = NamespacesOperations(config)
}

private[kubernetesclient] case class NamespacesOperations(config: KubeConfig)(
  implicit val system: ActorSystem,
  val encoder: Encoder[Namespace]
) extends Creatable[Namespace] {
  val resourceUri = s"${config.server}/api/v1/namespaces"

  def apply(namespace: String) = NamespaceOperations(config, namespace)
}

private[kubernetesclient] case class NamespaceOperations(private val config: KubeConfig, private val namespace: String)(
  implicit system: ActorSystem
) {
  lazy val pods = PodsOperations(config, namespace)
  lazy val jobs = JobsOperations(config, namespace)
  lazy val cronJobs = CronJobsOperations(config, namespace)
  lazy val deployments = DeploymentsOperations(config, namespace)
  lazy val services = ServicesOperations(config, namespace)
}
