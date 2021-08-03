package com.goyeau.kubernetes.client.api

import cats.effect.{Async, IO}
import cats.implicits._
import com.goyeau.kubernetes.client.api.ExecStream.{StdErr, StdOut}
import com.goyeau.kubernetes.client.operation._
import com.goyeau.kubernetes.client.{KubernetesClient, Utils}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.k8s.api.core.v1._
import io.k8s.apimachinery.pkg.apis.meta.v1
import io.k8s.apimachinery.pkg.apis.meta.v1.{ListMeta, ObjectMeta}
import munit.FunSuite
import org.http4s.Status
import cats.effect.unsafe.implicits.global

class PodsApiTest
    extends FunSuite
    with CreatableTests[IO, Pod]
    with GettableTests[IO, Pod]
    with ListableTests[IO, Pod, PodList]
    with ReplaceableTests[IO, Pod]
    with DeletableTests[IO, Pod, PodList]
    with DeletableTerminatedTests[IO, Pod, PodList]
    with WatchableTests[IO, Pod]
    with ContextProvider {

  implicit lazy val F: Async[IO]       = IO.asyncForIO
  implicit lazy val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
  lazy val resourceName                = classOf[Pod].getSimpleName

  override def api(implicit client: KubernetesClient[IO]) = client.pods
  override def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]) =
    client.pods.namespace(namespaceName)

  override def sampleResource(resourceName: String, labels: Map[String, String]) =
    Pod(
      metadata = Option(ObjectMeta(name = Option(resourceName), labels = Option(labels))),
      spec = Option(PodSpec(containers = Seq(Container("test", image = Option("docker")))))
    )
  val activeDeadlineSeconds = Option(5L)
  override def modifyResource(resource: Pod) =
    resource.copy(
      metadata = Option(ObjectMeta(name = resource.metadata.flatMap(_.name))),
      spec = resource.spec.map(_.copy(activeDeadlineSeconds = activeDeadlineSeconds))
    )
  override def checkUpdated(updatedResource: Pod) =
    assertEquals(updatedResource.spec.flatMap(_.activeDeadlineSeconds), activeDeadlineSeconds)

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.pods.namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, Pod] =
    client.pods.namespace(namespaceName)

  test("exec into pod") {
    val namespaceName = resourceName.toLowerCase
    val podName       = s"${resourceName.toLowerCase}-exec"
    val testPod = Pod(
      metadata = Option(ObjectMeta(name = Option(podName))),
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
          _ = assertEquals(status, Status.Created)
          pod <- waitUntilReady(namespaceName, podName)
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

    assertEquals(status, Right(v1.Status(status = Some("Success"), metadata = Some(ListMeta()))))

    assertNotEquals(messages.length, 0)

    val stdOut = messages
      .collect { case StdOut(d) =>
        d
      }
      .mkString("")
    val expectedDirs = List("dev", "etc", "home", "usr", "var").mkString("|").r

    assert(expectedDirs.findFirstIn(stdOut).isDefined)
    assert(expectedDirs.findFirstIn(messages.map(_.data).mkString("")).isDefined)
    assertEquals(messages.collect { case StdErr(d) => d }.length, 0)
  }

  val podStatusCount = 4
  def waitUntilReady(namespaceName: String, name: String)(implicit client: KubernetesClient[IO]): IO[Pod] =
    Utils.retry(for {
      pod <- getChecked(namespaceName, name)
      notStarted  = pod.status.flatMap(_.conditions.map(_.exists(c => c.status == "False"))).getOrElse(false)
      statusCount = pod.status.flatMap(_.conditions.map(_.length)).getOrElse(0)
      _ <-
        if (notStarted || statusCount != podStatusCount)
          IO.raiseError(new RuntimeException("Pod is not started"))
        else IO.unit
    } yield pod)
}
