package com.goyeau.kubernetes.client.api

import cats.effect.{Async, IO}
import com.goyeau.kubernetes.client.{KubernetesClient, TestPodSpec}
import com.goyeau.kubernetes.client.operation.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.k8s.api.apps.v1.*
import io.k8s.api.core.v1.*
import io.k8s.apimachinery.pkg.apis.meta.v1.{LabelSelector, ObjectMeta}
import munit.FunSuite
import fs2.io.file.Files

class ReplicaSetsApiTest
    extends FunSuite
    with CreatableTests[IO, ReplicaSet]
    with GettableTests[IO, ReplicaSet]
    with ListableTests[IO, ReplicaSet, ReplicaSetList]
    with ReplaceableTests[IO, ReplicaSet]
    with DeletableTests[IO, ReplicaSet, ReplicaSetList]
    with DeletableTerminatedTests[IO, ReplicaSet, ReplicaSetList]
    with WatchableTests[IO, ReplicaSet]
    with ContextProvider {

  implicit override lazy val F: Async[IO]       = IO.asyncForIO
  implicit override lazy val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
  implicit override lazy val G: Files[IO]       = Files.forIO
  override lazy val resourceName: String        = classOf[ReplicaSet].getSimpleName

  override def api(implicit client: KubernetesClient[IO]): ReplicaSetsApi[IO] = client.replicaSets
  override def namespacedApi(namespaceName: String)(implicit
      client: KubernetesClient[IO]
  ): NamespacedReplicaSetsApi[IO] =
    client.replicaSets.namespace(namespaceName)

  override def sampleResource(resourceName: String, labels: Map[String, String]): ReplicaSet = {
    val label = Option(Map("app" -> "test"))
    ReplicaSet(
      metadata = Option(ObjectMeta(name = Option(resourceName), labels = Option(labels))),
      spec = Option(
        ReplicaSetSpec(
          selector = LabelSelector(matchLabels = label),
          template = Option(
            PodTemplateSpec(
              metadata = Option(ObjectMeta(name = Option(resourceName), labels = label)),
              spec = Option(TestPodSpec.alpine)
            )
          )
        )
      )
    )
  }

  private val replicas                                          = Option(5)
  override def modifyResource(resource: ReplicaSet): ReplicaSet = resource.copy(
    metadata = Option(ObjectMeta(name = resource.metadata.flatMap(_.name))),
    spec = resource.spec.map(_.copy(replicas = replicas))
  )
  override def checkUpdated(updatedResource: ReplicaSet): Unit =
    assertEquals(updatedResource.spec.flatMap(_.replicas), replicas)

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.replicaSets.namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, ReplicaSet] =
    client.replicaSets.namespace(namespaceName)
}
