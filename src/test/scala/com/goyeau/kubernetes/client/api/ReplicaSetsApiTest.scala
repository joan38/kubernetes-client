package com.goyeau.kubernetes.client.api

import cats.effect.{ConcurrentEffect, IO}
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

class ReplicaSetsApiTest
    extends AnyFlatSpec
    with Matchers
    with OptionValues
    with CreatableTests[IO, ReplicaSet]
    with GettableTests[IO, ReplicaSet]
    with ListableTests[IO, ReplicaSet, ReplicaSetList]
    with ReplaceableTests[IO, ReplicaSet]
    with DeletableTests[IO, ReplicaSet, ReplicaSetList]
    with DeletableTerminatedTests[IO, ReplicaSet, ReplicaSetList]
    with WatchableTests[IO, ReplicaSet]
    with ContextProvider {

  implicit lazy val F: ConcurrentEffect[IO] = IO.ioConcurrentEffect
  implicit lazy val logger: Logger[IO]      = Slf4jLogger.getLogger[IO]
  lazy val resourceName                     = classOf[ReplicaSet].getSimpleName

  override def api(implicit client: KubernetesClient[IO]) = client.replicaSets
  override def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]) =
    client.replicaSets.namespace(namespaceName)

  override def sampleResource(resourceName: String, labels: Map[String, String]) = {
    val label = Option(Map("app" -> "test"))
    ReplicaSet(
      metadata = Option(ObjectMeta(name = Option(resourceName), labels = Option(labels))),
      spec = Option(
        ReplicaSetSpec(
          selector = LabelSelector(matchLabels = label),
          template = Option(
            PodTemplateSpec(
              metadata = Option(ObjectMeta(name = Option(resourceName), labels = label)),
              spec = Option(PodSpec(containers = Seq(Container("test", image = Option("docker")))))
            )
          )
        )
      )
    )
  }
  val replicas = Option(5)
  override def modifyResource(resource: ReplicaSet) = resource.copy(
    metadata = Option(ObjectMeta(name = resource.metadata.flatMap(_.name))),
    spec = resource.spec.map(_.copy(replicas = replicas))
  )
  override def checkUpdated(updatedResource: ReplicaSet) =
    updatedResource.spec.value.replicas shouldBe replicas

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.replicaSets.namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, ReplicaSet] =
    client.replicaSets.namespace(namespaceName)
}
