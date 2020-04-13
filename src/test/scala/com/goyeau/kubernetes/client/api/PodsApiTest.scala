package com.goyeau.kubernetes.client.api

import cats.effect.{ConcurrentEffect, ContextShift, IO, Timer}
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.operation._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.k8s.api.core.v1.{Container, Pod, PodList, PodSpec}
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers

class PodsApiTest
    extends AnyFlatSpec
    with Matchers
    with OptionValues
    with CreatableTests[IO, Pod]
    with GettableTests[IO, Pod]
    with ListableTests[IO, Pod, PodList]
    with ReplaceableTests[IO, Pod]
    with DeletableTests[IO, Pod, PodList]
    with DeletableTerminatedTests[IO, Pod, PodList]
    with WatchableTests[IO, Pod]
    with ContextProvider {

  implicit lazy val F: ConcurrentEffect[IO] = IO.ioConcurrentEffect
  implicit lazy val logger: Logger[IO]      = Slf4jLogger.getLogger[IO]
  lazy val resourceName                     = classOf[Pod].getSimpleName

  override def api(implicit client: KubernetesClient[IO]) = client.pods
  override def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]) =
    client.pods.namespace(namespaceName)

  override def sampleResource(resourceName: String) = Pod(
    metadata = Option(ObjectMeta(name = Option(resourceName))),
    spec = Option(PodSpec(nodeName = Some("minikube"), containers = Seq(Container("test", image = Option("docker")))))
  )
  val activeDeadlineSeconds = Option(5)
  override def modifyResource(resource: Pod) = resource.copy(
    metadata = Option(ObjectMeta(name = resource.metadata.flatMap(_.name))),
    spec = resource.spec.map(_.copy(activeDeadlineSeconds = activeDeadlineSeconds))
  )
  override def checkUpdated(updatedResource: Pod) =
    updatedResource.spec.value.activeDeadlineSeconds shouldBe activeDeadlineSeconds

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.pods.namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, Pod] =
    client.pods.namespace(namespaceName)
}
