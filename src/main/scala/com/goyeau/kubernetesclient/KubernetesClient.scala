package com.goyeau.kubernetesclient

import akka.actor.ActorSystem
import io.circe.generic.auto._

case class KubernetesClient(config: KubeConfig)(implicit system: ActorSystem) {
  lazy val namespaces = NamespacesOperations(config)
  lazy val pods = PodsOperations(config)
  lazy val jobs = JobsOperations(config)
  lazy val cronJobs = CronJobsOperations(config)
  lazy val deployments = DeploymentsOperations(config)
  lazy val statefulSets = StatefulSetsOperations(config)
  lazy val services = ServicesOperations(config)
  lazy val configMaps = ConfigMapsOperations(config)
  lazy val secrets = SecretsOperations(config)
  lazy val horizontalPodAutoscalers = HorizontalPodAutoscalersOperations(config)
}
