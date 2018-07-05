package com.goyeau.kubernetes.client

import akka.actor.ActorSystem

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
  lazy val podDisruptionBudgets = PodDisruptionBudgetsOperations(config)
}
