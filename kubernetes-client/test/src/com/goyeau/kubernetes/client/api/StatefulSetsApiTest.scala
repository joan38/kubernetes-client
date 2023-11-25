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

class StatefulSetsApiTest
    extends MinikubeClientProvider
    with CreatableTests[StatefulSet]
    with GettableTests[StatefulSet]
    with ListableTests[StatefulSet, StatefulSetList]
    with ReplaceableTests[StatefulSet]
    with DeletableTests[StatefulSet, StatefulSetList]
    with DeletableTerminatedTests[StatefulSet, StatefulSetList]
    with WatchableTests[StatefulSet]
     {

  implicit override lazy val logger: Logger[IO] = TestPlatformSpecific.getLogger
  override lazy val resourceName: String        = classOf[StatefulSet].getSimpleName

  override def api(implicit client: KubernetesClient[IO]): StatefulSetsApi[IO] = client.statefulSets
  override def namespacedApi(namespaceName: String)(implicit
      client: KubernetesClient[IO]
  ): NamespacedStatefulSetsApi[IO] =
    client.statefulSets.namespace(namespaceName)

  override def sampleResource(resourceName: String, labels: Map[String, String]): StatefulSet = {
    val label = Option(Map("app" -> "test"))
    StatefulSet(
      metadata = Option(ObjectMeta(name = Option(resourceName), labels = Option(labels))),
      spec = Option(
        StatefulSetSpec(
          serviceName = "service-name",
          selector = LabelSelector(matchLabels = label),
          template = PodTemplateSpec(
            metadata = Option(ObjectMeta(name = Option(resourceName), labels = label)),
            spec = Option(TestPodSpec.alpine)
          )
        )
      )
    )
  }

  private val updateStrategy = Option(
    StatefulSetUpdateStrategy(
      `type` = Option("RollingUpdate"),
      rollingUpdate = Option(RollingUpdateStatefulSetStrategy(partition = Option(10)))
    )
  )
  override def modifyResource(resource: StatefulSet): StatefulSet =
    resource.copy(
      metadata = Option(ObjectMeta(name = resource.metadata.flatMap(_.name))),
      spec = resource.spec.map(_.copy(updateStrategy = updateStrategy))
    )
  override def checkUpdated(updatedResource: StatefulSet): Unit =
    assertEquals(updatedResource.spec.flatMap(_.updateStrategy), updateStrategy)

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.statefulSets.namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, StatefulSet] =
    client.statefulSets.namespace(namespaceName)
}
