package com.goyeau.kubernetes.client.api

import cats.effect.*
import com.goyeau.kubernetes.client.{KubernetesClient, TestPodSpec}
import com.goyeau.kubernetes.client.MinikubeClientProvider
import com.goyeau.kubernetes.client.operation.*
import org.typelevel.log4cats.Logger
import com.goyeau.kubernetes.client.TestPlatformSpecific
import io.k8s.api.apps.v1.*
import io.k8s.api.core.v1.*
import io.k8s.apimachinery.pkg.apis.meta.v1.{LabelSelector, ObjectMeta}

class ReplicaSetsApiTest
    extends MinikubeClientProvider
    with CreatableTests[ReplicaSet]
    with GettableTests[ReplicaSet]
    with ListableTests[ReplicaSet, ReplicaSetList]
    with ReplaceableTests[ReplicaSet]
    with DeletableTests[ReplicaSet, ReplicaSetList]
    with DeletableTerminatedTests[ReplicaSet, ReplicaSetList]
    with WatchableTests[ReplicaSet]
     {

  implicit override lazy val logger: Logger[IO] = TestPlatformSpecific.getLogger
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

  private val replicas = Option(5)
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
