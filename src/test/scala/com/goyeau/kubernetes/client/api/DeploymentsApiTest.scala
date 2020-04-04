package com.goyeau.kubernetes.client.api

import cats.effect.{ConcurrentEffect, ContextShift, IO, Timer}
import com.goyeau.kubernetes.client.operation._
import com.goyeau.kubernetes.client.{IntValue, KubernetesClient, StringValue}
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.k8s.api.apps.v1._
import io.k8s.api.core.v1._
import io.k8s.apimachinery.pkg.apis.meta.v1.{LabelSelector, ObjectMeta}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext

class DeploymentsApiTest
    extends AnyFlatSpec
    with Matchers
    with OptionValues
    with CreatableTests[IO, Deployment]
    with GettableTests[IO, Deployment]
    with ListableTests[IO, Deployment, DeploymentList]
    with ReplaceableTests[IO, Deployment]
    with DeletableTests[IO, Deployment, DeploymentList]
    with DeletableTerminatedTests[IO, Deployment, DeploymentList] {

  implicit lazy val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  implicit lazy val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit lazy val F: ConcurrentEffect[IO] = IO.ioConcurrentEffect
  implicit lazy val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
  lazy val resourceName = classOf[Deployment].getSimpleName

  override def api(implicit client: KubernetesClient[IO]) = client.deployments
  override def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]) =
    client.deployments.namespace(namespaceName)

  override def sampleResource(resourceName: String) = {
    val label = Option(Map("app" -> "test"))
    Deployment(
      metadata = Option(ObjectMeta(name = Option(resourceName))),
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
    updatedResource.spec.value.strategy shouldBe strategy
}
