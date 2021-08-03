package com.goyeau.kubernetes.client.api

import cats.effect.{Async, IO}
import com.goyeau.kubernetes.client.operation._
import com.goyeau.kubernetes.client.{IntValue, KubernetesClient, StringValue}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.k8s.api.apps.v1._
import io.k8s.api.core.v1._
import io.k8s.apimachinery.pkg.apis.meta.v1.{LabelSelector, ObjectMeta}
import munit.FunSuite

class DeploymentsApiTest
    extends FunSuite
    with CreatableTests[IO, Deployment]
    with GettableTests[IO, Deployment]
    with ListableTests[IO, Deployment, DeploymentList]
    with ReplaceableTests[IO, Deployment]
    with DeletableTests[IO, Deployment, DeploymentList]
    with DeletableTerminatedTests[IO, Deployment, DeploymentList]
    with WatchableTests[IO, Deployment]
    with ContextProvider {

  implicit lazy val F: Async[IO]       = IO.asyncForIO
  implicit lazy val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
  lazy val resourceName                = classOf[Deployment].getSimpleName

  override def api(implicit client: KubernetesClient[IO]) = client.deployments
  override def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]) =
    client.deployments.namespace(namespaceName)

  override def sampleResource(resourceName: String, labels: Map[String, String]) = {
    val label = Option(Map("app" -> "test"))
    Deployment(
      metadata = Option(ObjectMeta(name = Option(resourceName), labels = Option(labels))),
      spec = Option(
        DeploymentSpec(
          selector = LabelSelector(matchLabels = label),
          template = PodTemplateSpec(
            metadata = Option(ObjectMeta(name = Option(resourceName), labels = label)),
            spec = Option(PodSpec(containers = Seq(Container("test", image = Option("docker")))))
          )
        )
      )
    )
  }
  val strategy = Option(
    DeploymentStrategy(
      `type` = Option("RollingUpdate"),
      rollingUpdate =
        Option(RollingUpdateDeployment(maxSurge = Option(StringValue("25%")), maxUnavailable = Option(IntValue(10))))
    )
  )
  override def modifyResource(resource: Deployment) = resource.copy(
    metadata = Option(ObjectMeta(name = resource.metadata.flatMap(_.name))),
    spec = resource.spec.map(_.copy(strategy = strategy))
  )
  override def checkUpdated(updatedResource: Deployment) =
    assertEquals(updatedResource.spec.flatMap(_.strategy), strategy)

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.deployments.namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, Deployment] =
    client.deployments.namespace(namespaceName)
}
