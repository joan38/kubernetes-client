package com.goyeau.kubernetes.client.api

import cats.effect.{ConcurrentEffect, ContextShift, IO, Timer}
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.operation._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.k8s.api.apps.v1._
import io.k8s.api.core.v1._
import io.k8s.apimachinery.pkg.apis.meta.v1.{LabelSelector, ObjectMeta}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext

class StatefulSetsApiTest
    extends AnyFlatSpec
    with Matchers
    with OptionValues
    with CreatableTests[IO, StatefulSet]
    with GettableTests[IO, StatefulSet]
    with ListableTests[IO, StatefulSet, StatefulSetList]
    with ReplaceableTests[IO, StatefulSet]
    with DeletableTests[IO, StatefulSet, StatefulSetList]
    with DeletableTerminatedTests[IO, StatefulSet, StatefulSetList] {

  implicit lazy val timer: Timer[IO]               = IO.timer(ExecutionContext.global)
  implicit lazy val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit lazy val F: ConcurrentEffect[IO]        = IO.ioConcurrentEffect
  implicit lazy val logger: Logger[IO]             = Slf4jLogger.getLogger[IO]
  lazy val resourceName                            = classOf[StatefulSet].getSimpleName

  override def api(implicit client: KubernetesClient[IO]) = client.statefulSets
  override def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]) =
    client.statefulSets.namespace(namespaceName)

  override def sampleResource(resourceName: String) = {
    val label = Option(Map("app" -> "test"))
    StatefulSet(
      metadata = Option(ObjectMeta(name = Option(resourceName))),
      spec = Option(
        StatefulSetSpec(
          serviceName = "service-name",
          selector = LabelSelector(matchLabels = label),
          template = PodTemplateSpec(
            metadata = Option(ObjectMeta(name = Option(resourceName), labels = label)),
            spec = Option(PodSpec(containers = Seq(Container("test", image = Option("docker")))))
          )
        )
      )
    )
  }
  val updateStrategy = Option(
    StatefulSetUpdateStrategy(
      `type` = Option("RollingUpdate"),
      rollingUpdate = Option(RollingUpdateStatefulSetStrategy(partition = Option(10)))
    )
  )
  override def modifyResource(resource: StatefulSet) = resource.copy(
    metadata = Option(ObjectMeta(name = resource.metadata.flatMap(_.name))),
    spec = resource.spec.map(_.copy(updateStrategy = updateStrategy))
  )
  override def checkUpdated(updatedResource: StatefulSet) =
    updatedResource.spec.value.updateStrategy shouldBe updateStrategy
}
