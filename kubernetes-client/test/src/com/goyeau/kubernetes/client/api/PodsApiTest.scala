package com.goyeau.kubernetes.client.api

import cats.effect.unsafe.implicits.global
import cats.effect.{Async, IO}
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.Utils.retry
import com.goyeau.kubernetes.client.api.ExecStream.{StdErr, StdOut}
import com.goyeau.kubernetes.client.operation.*
import fs2.io.file.{Files, Path}
import fs2.{text, Stream}
import io.k8s.api.core.v1.*
import io.k8s.apimachinery.pkg.apis.meta.v1
import io.k8s.apimachinery.pkg.apis.meta.v1.{ListMeta, ObjectMeta}
import munit.FunSuite
import org.http4s.Status
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import java.nio.file.Files as JFiles
import scala.util.Random

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

  implicit override lazy val F: Async[IO]       = IO.asyncForIO
  implicit override lazy val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
  override lazy val resourceName: String        = classOf[Pod].getSimpleName

  override def api(implicit client: KubernetesClient[IO]): PodsApi[IO] = client.pods
  override def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]): NamespacedPodsApi[IO] =
    client.pods.namespace(namespaceName)

  override def sampleResource(resourceName: String, labels: Map[String, String]): Pod =
    Pod(
      metadata = Option(ObjectMeta(name = Option(resourceName), labels = Option(labels))),
      spec = Option(PodSpec(containers = Seq(Container("test", image = Option("docker")))))
    )

  private val activeDeadlineSeconds = Option(5L)
  override def modifyResource(resource: Pod): Pod =
    resource.copy(
      metadata = Option(ObjectMeta(name = resource.metadata.flatMap(_.name))),
      spec = resource.spec.map(_.copy(activeDeadlineSeconds = activeDeadlineSeconds))
    )

  override def checkUpdated(updatedResource: Pod): Unit =
    assertEquals(updatedResource.spec.flatMap(_.activeDeadlineSeconds), activeDeadlineSeconds)

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.pods.namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, Pod] =
    client.pods.namespace(namespaceName)

  def testPod(podName: String, labels: Map[String, String] = Map.empty): Pod = Pod(
    metadata = Option(ObjectMeta(name = Option(podName), labels = Option(labels))),
    spec = Option(
      PodSpec(
        containers = Seq(
          Container(
            "test",
            image = Option("docker"),
            command = Option(Seq("sh", "-c", "tail -f /dev/null"))
          )
        )
      )
    )
  )

  def testPodWithLogs(podName: String, labels: Map[String, String] = Map.empty): Pod = Pod(
    metadata = Option(ObjectMeta(name = Option(podName), labels = Option(labels))),
    spec = Option(
      PodSpec(
        containers = Seq(
          Container(
            "test",
            image = Option("docker"),
            command = Option(
              Seq(
                "sh",
                "-c",
                "echo line 1; sleep 1; echo line 2; sleep 2; echo line 3; echo line 4; echo line 5; echo line 6"
              )
            )
          )
        )
      )
    )
  )

  private val successStatus = Some(Right(v1.Status(status = Some("Success"), metadata = Some(ListMeta()))))

  test("exec into pod") {
    val podName = s"${resourceName.toLowerCase}-exec"
    val (messages, status) = kubernetesClient
      .use { implicit client =>
        for {
          status <- namespacedApi(defaultNamespace).create(testPod(podName))
          _ = assertEquals(status, Status.Created)
          pod <- waitUntilReady(defaultNamespace, podName)
          res <- namespacedApi(defaultNamespace).exec(
            pod.metadata.get.name.get,
            pod.spec.flatMap(_.containers.headOption.map(_.name)),
            Seq("ls")
          )
        } yield res
      }
      .unsafeRunSync()

    assertEquals(status, successStatus)
    assertNotEquals(messages.length, 0)

    val stdOut = messages
      .collect { case o: StdOut =>
        o.asString
      }
      .mkString("")
    val expectedDirs = Seq("dev", "etc", "home", "usr", "var").mkString("|").r

    assert(expectedDirs.findFirstIn(stdOut).isDefined, stdOut)

    val errors = messages.collect { case e: StdErr => e }
    assertEquals(errors.length, 0)
  }

  test("download a file") {
    val podName = s"${resourceName.toLowerCase}-download"
    val (messages, status) = kubernetesClient
      .use { implicit client =>
        for {
          status <- namespacedApi(defaultNamespace).create(testPod(podName))
          _ = assertEquals(status, Status.Created, status.sanitizedReason)
          pod <- waitUntilReady(defaultNamespace, podName)
          res <- namespacedApi(defaultNamespace).download(
            pod.metadata.get.name.get,
            Path("/etc/sysctl.conf"),
            Path("./out/sysctl.conf"),
            pod.spec.flatMap(_.containers.headOption.map(_.name))
          )
        } yield res
      }
      .unsafeRunSync()

    assertEquals(status, successStatus)
    assertEquals(messages.length, 0, messages.map(_.asString).mkString(""))
  }

  private def tempFile =
    F.delay(Path.fromNioPath(JFiles.createTempFile("test-file-", ".txt")))

  private def fileExists(podName: String, container: Option[String], targetPath: Path)(implicit
      client: KubernetesClient[IO]
  ) =
    for {
      list <- namespacedApi(defaultNamespace).exec(podName, container, Seq("ls", targetPath.toString))
      (messages, status) = list
      clue               = messages.map(_.asString).mkString("\n")
      _ = assertEquals(
        status,
        successStatus,
        s"Failed to list file in $defaultNamespace/$podName: $clue"
      )
    } yield ()

  private def uploadFile(podName: String, sourcePath: Path, targetPath: Path, container: Option[String])(implicit
      client: KubernetesClient[IO]
  ) = for {
    uploaded <- namespacedApi(defaultNamespace).upload(
      podName,
      sourcePath,
      targetPath,
      container
    )
    (messages, status) = uploaded
    _ = assertEquals(
      messages.length,
      0,
      s"sourcePath: $sourcePath, targetPath: $targetPath, stdout: " + messages.map(_.asString).mkString("")
    )
    _ = assertEquals(status, successStatus)
  } yield ()

  private def writeTempFile(source: Path) =
    Stream
      .emit(Random.alphanumeric.take(200).mkString)
      .take(200)
      .through(text.utf8.encode)
      .through(Files[IO].writeAll(source))
      .compile
      .drain

  private def downloadFile(podName: String, targetPath: Path, downloadPath: Path, container: Option[String])(implicit
      client: KubernetesClient[IO]
  ) = for {
    downloaded <- namespacedApi(defaultNamespace).download(
      podName,
      targetPath,
      downloadPath,
      container
    )
    (errors, status) = downloaded
    _                = assertEquals(status, successStatus)
    _ = assertEquals(
      errors.length,
      0,
      s"targetPath: $targetPath, downloadPath: $downloadPath, stderr: " + errors.map(_.asString).mkString("")
    )
  } yield ()

  test("upload and then download a file") {
    val podName = s"${resourceName.toLowerCase}-upload"

    kubernetesClient
      .use { implicit client =>
        for {
          status <- namespacedApi(defaultNamespace).create(testPod(podName))
          _ = assertEquals(status, Status.Created, status.sanitizedReason)
          pod        <- waitUntilReady(defaultNamespace, podName)
          sourcePath <- tempFile
          _          <- writeTempFile(sourcePath)
          targetPath = Path("/upload") / sourcePath.fileName
          container  = pod.spec.flatMap(_.containers.headOption.map(_.name))
          podName    = pod.metadata.get.name.get

          _            <- uploadFile(podName, sourcePath, targetPath, container)
          _            <- fileExists(podName, container, targetPath)
          downloadPath <- tempFile
          _            <- downloadFile(podName, targetPath, downloadPath, container)

          localContent <-
            Files[IO]
              .readAll(sourcePath)
              .through(text.utf8.decode)
              .compile
              .toList
          downloadedContent <- Files[IO].readAll(downloadPath).through(text.utf8.decode).compile.toList

          _ = assertEquals(
            downloadedContent.mkString,
            localContent.mkString,
            s"Files at $sourcePath and $downloadPath paths are not equal"
          )
        } yield ()
      }
      .unsafeRunSync()
  }

  test("watch the logs") {
    val podName = s"${resourceName.toLowerCase}-logs"

    kubernetesClient
      .use { implicit client =>
        for {
          status <- namespacedApi(defaultNamespace).create(testPodWithLogs(podName))
          _ = assertEquals(status, Status.Created, status.sanitizedReason)
          pod <- waitUntilReady(defaultNamespace, podName)
          container = pod.spec.flatMap(_.containers.headOption.map(_.name))
          podName   = pod.metadata.get.name.get

          receivedLogs <- namespacedApi(defaultNamespace)
            .log(
              podName,
              container = container,
              follow = true
            )
            .flatMap { response =>
              response.body
            }
            .through(fs2.text.utf8.decode)
            .compile
            .foldMonoid

          _ = assertEquals(
            receivedLogs,
            """line 1
              |line 2
              |line 3
              |line 4
              |line 5
              |line 6
              |""".stripMargin,
            s"Received log doesn't match the expected log"
          )
        } yield ()
      }
      .unsafeRunSync()
  }

  private val podStatusCount = 4

  def waitUntilReady(namespaceName: String, name: String)(implicit client: KubernetesClient[IO]): IO[Pod] =
    retry(
      for {
        pod <- getChecked(namespaceName, name)
        notStarted  = pod.status.flatMap(_.conditions.map(_.exists(c => c.status == "False"))).getOrElse(false)
        statusCount = pod.status.flatMap(_.conditions.map(_.length)).getOrElse(0)
        _ <-
          if (notStarted || statusCount != podStatusCount)
            IO.raiseError(new RuntimeException("Pod is not started"))
          else IO.unit
      } yield pod,
      actionClue = Some(s"Waiting for pod $name to be ready")
    )
}
