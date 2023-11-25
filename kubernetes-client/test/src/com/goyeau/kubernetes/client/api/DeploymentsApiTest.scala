package com.goyeau.kubernetes.client.api

import cats.effect.*
import com.goyeau.kubernetes.client.operation.*
import com.goyeau.kubernetes.client.{IntValue, KubernetesClient, StringValue, TestPodSpec}
import com.goyeau.kubernetes.client.MinikubeClientProvider
import org.typelevel.log4cats.Logger
import com.goyeau.kubernetes.client.TestPlatformSpecific
import io.k8s.api.apps.v1.*
import io.k8s.api.core.v1.*
import io.k8s.apimachinery.pkg.apis.meta.v1.{LabelSelector, ObjectMeta}

class DeploymentsApiTest
    extends MinikubeClientProvider
    with CreatableTests[Deployment]
    with GettableTests[Deployment]
    with ListableTests[Deployment, DeploymentList]
    with ReplaceableTests[Deployment]
    with DeletableTests[Deployment, DeploymentList]
    with DeletableTerminatedTests[Deployment, DeploymentList]
    with WatchableTests[Deployment]
     {

  implicit override lazy val logger: Logger[IO] = TestPlatformSpecific.getLogger
  override lazy val resourceName: String        = classOf[Deployment].getSimpleName

  override def api(implicit client: KubernetesClient[IO]): DeploymentsApi[IO] = client.deployments
  override def namespacedApi(namespaceName: String)(implicit
      client: KubernetesClient[IO]
  ): NamespacedDeploymentsApi[IO] =
    client.deployments.namespace(namespaceName)

  override def sampleResource(resourceName: String, labels: Map[String, String]): Deployment = {
    val label = Option(Map("app" -> "test"))
    Deployment(
      metadata = Option(ObjectMeta(name = Option(resourceName), labels = Option(labels))),
      spec = Option(
        DeploymentSpec(
          selector = LabelSelector(matchLabels = label),
          template = PodTemplateSpec(
            metadata = Option(ObjectMeta(name = Option(resourceName), labels = label)),
            spec = Option(TestPodSpec.alpine)
          )
        )
      )
    )
  }

  private val strategy = Option(
    DeploymentStrategy(
      `type` = Option("RollingUpdate"),
      rollingUpdate =
        Option(RollingUpdateDeployment(maxSurge = Option(StringValue("25%")), maxUnavailable = Option(IntValue(10))))
    )
  )
  override def modifyResource(resource: Deployment): Deployment = resource.copy(
    metadata = Option(ObjectMeta(name = resource.metadata.flatMap(_.name))),
    spec = resource.spec.map(_.copy(strategy = strategy))
  )
  override def checkUpdated(updatedResource: Deployment): Unit =
    assertEquals(updatedResource.spec.flatMap(_.strategy), strategy)

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.deployments.namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, Deployment] =
    client.deployments.namespace(namespaceName)
}
