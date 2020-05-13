package com.goyeau.kubernetes.client.api

import cats.effect.{ConcurrentEffect, IO}
import cats.implicits._
import com.goyeau.kubernetes.client.operation._
import com.goyeau.kubernetes.client.{KubernetesClient, Utils}
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.k8s.api.core.v1._
import io.k8s.apimachinery.pkg.apis.meta.v1
import io.k8s.apimachinery.pkg.apis.meta.v1.{ListMeta, ObjectMeta}
import org.http4s.Status
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
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

  override def sampleResource(resourceName: String, labels: Map[String, String]) =
    Pod(
      metadata = Option(ObjectMeta(name = Option(resourceName), labels = Option(labels))),
      spec = Option(PodSpec(containers = Seq(Container("test", image = Option("busybox")))))
    )
  val activeDeadlineSeconds = Option(5L)
  override def modifyResource(resource: Pod) =
    resource.copy(
      metadata = Option(ObjectMeta(name = resource.metadata.flatMap(_.name))),
      spec = resource.spec.map(_.copy(activeDeadlineSeconds = activeDeadlineSeconds))
    )
  override def checkUpdated(updatedResource: Pod) =
    updatedResource.spec.value.activeDeadlineSeconds shouldBe activeDeadlineSeconds

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.pods.namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, Pod] =
    client.pods.namespace(namespaceName)

  it should "exec into pod" in {
    val namespaceName = resourceName.toLowerCase
    val name          = resourceName.toLowerCase + "-exec"
    val testPod = Pod(
      metadata = Option(ObjectMeta(name = Option(name))),
      spec = Option(
        PodSpec(containers =
          Seq(
            Container(
              "test",
              image = Option("docker"),
              command = Option(Seq("sh", "-c", "tail -f /dev/null"))
            )
          )
        )
      )
    )
    val res = kubernetesClient
      .use { implicit client =>
        for {
          status <- namespacedApi(namespaceName).create(testPod)
          _ = status shouldBe Status.Created
          pod <- waitUntilReady(namespaceName, name)
          res <- namespacedApi(namespaceName).exec(
            pod.metadata.get.name.get,
            (es: ExecStream) => List(es),
            pod.spec.flatMap(_.containers.headOption.map(_.name)),
            Seq("ls")
          )
        } yield res
      }
      .unsafeRunSync()
    val (messages, status) = res

    status should be(Right(v1.Status(status = Some("Success"), metadata = Some(ListMeta()))))

    messages.length shouldNot be(0)
    val stdOut = messages.collect {
      case StdOut(d) => d
    }.mkString("")
    stdOut should include regex("dev|etc|home|usr|var")

    messages.collect { case StdErr(d) => d }.length should be(0)
  }

  def waitUntilReady(namespaceName: String, name: String)(implicit client: KubernetesClient[IO]): IO[Pod] =
    Utils.retry(for {
      pod <- getChecked(namespaceName, name)
      notStarted  = pod.status.flatMap(_.conditions.map(_.exists(c => c.status == "False"))).getOrElse(false)
      statusCount = pod.status.flatMap(_.conditions.map(_.length)).getOrElse(0)
      _ <-
        if (notStarted || statusCount != 4)
          IO.raiseError(new RuntimeException("Pod is not started"))
        else IO.unit
    } yield pod)
}
