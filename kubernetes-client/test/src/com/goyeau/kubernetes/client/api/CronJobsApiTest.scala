package com.goyeau.kubernetes.client.api

import cats.syntax.all.*
import cats.effect.{Async, IO}
import com.goyeau.kubernetes.client.operation.*
import com.goyeau.kubernetes.client.{KubernetesClient, TestPodSpec}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.k8s.api.batch.v1.{CronJob, CronJobList, CronJobSpec, JobSpec, JobTemplateSpec}
import io.k8s.api.core.v1.*
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import munit.FunSuite
import fs2.io.file.Files

class CronJobsApiTest
    extends FunSuite
    with CreatableTests[IO, CronJob]
    with GettableTests[IO, CronJob]
    with ListableTests[IO, CronJob, CronJobList]
    with ReplaceableTests[IO, CronJob]
    with DeletableTests[IO, CronJob, CronJobList]
    with DeletableTerminatedTests[IO, CronJob, CronJobList]
    with WatchableTests[IO, CronJob]
    with ContextProvider {

  implicit override lazy val F: Async[IO]       = IO.asyncForIO
  implicit override lazy val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
  implicit override lazy val G: Files[IO]       = Files.forIO
  override lazy val resourceName: String        = classOf[CronJob].getSimpleName

  override def api(implicit client: KubernetesClient[IO]): CronJobsApi[IO] = client.cronJobs
  override def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]): NamespacedCronJobsApi[IO] =
    client.cronJobs.namespace(namespaceName)

  override def sampleResource(resourceName: String, labels: Map[String, String]): CronJob =
    CronJob(
      metadata = Option(ObjectMeta(name = Option(resourceName), labels = Option(labels))),
      spec = Option(
        CronJobSpec(
          schedule = "1 * * * *",
          jobTemplate = JobTemplateSpec(
            spec = Option(
              JobSpec(
                template = PodTemplateSpec(
                  metadata = Option(ObjectMeta(name = Option(resourceName))),
                  spec = TestPodSpec.alpine.copy(restartPolicy = "Never".some).some
                )
              )
            )
          )
        )
      )
    )

  private val schedule                                    = "2 * * * *"
  override def modifyResource(resource: CronJob): CronJob = resource.copy(
    metadata = Option(ObjectMeta(name = resource.metadata.flatMap(_.name))),
    spec = resource.spec.map(_.copy(schedule = schedule))
  )
  override def checkUpdated(updatedResource: CronJob): Unit =
    assertEquals(updatedResource.spec.map(_.schedule), Some(schedule))

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.cronJobs.namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, CronJob] =
    client.cronJobs.namespace(namespaceName)
}
