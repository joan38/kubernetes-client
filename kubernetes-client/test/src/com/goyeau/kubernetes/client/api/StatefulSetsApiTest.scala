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

class StatefulSetsApiTest
    extends FunSuite
    with CreatableTests[IO, StatefulSet]
    with GettableTests[IO, StatefulSet]
    with ListableTests[IO, StatefulSet, StatefulSetList]
    with ReplaceableTests[IO, StatefulSet]
    with DeletableTests[IO, StatefulSet, StatefulSetList]
    with DeletableTerminatedTests[IO, StatefulSet, StatefulSetList]
    with WatchableTests[IO, StatefulSet]
    with ContextProvider {

  implicit override lazy val F: Async[IO]       = IO.asyncForIO
  implicit override lazy val G: Files[IO]       = Files.forIO
  implicit override lazy val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
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
