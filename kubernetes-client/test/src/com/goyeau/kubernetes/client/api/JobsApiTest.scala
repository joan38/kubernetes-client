package com.goyeau.kubernetes.client.api

import cats.syntax.all.*
import cats.effect.*
import com.goyeau.kubernetes.client.operation.*
import com.goyeau.kubernetes.client.{KubernetesClient, TestPodSpec}
import com.goyeau.kubernetes.client.MinikubeClientProvider
import org.typelevel.log4cats.Logger
import com.goyeau.kubernetes.client.TestPlatformSpecific
import io.k8s.api.batch.v1.{Job, JobList, JobSpec}
import io.k8s.api.core.v1.*
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta

class JobsApiTest
    extends MinikubeClientProvider
    with CreatableTests[Job]
    with GettableTests[Job]
    with ListableTests[Job, JobList]
    with ReplaceableTests[Job]
    with DeletableTests[Job, JobList]
    with DeletableTerminatedTests[Job, JobList]
    with WatchableTests[Job]
     {

  implicit lazy val logger: Logger[IO] = TestPlatformSpecific.getLogger
  lazy val resourceName: String        = classOf[Job].getSimpleName

  override def api(implicit client: KubernetesClient[IO]): JobsApi[IO] = client.jobs
  override def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]): NamespacedJobsApi[IO] =
    client.jobs.namespace(namespaceName)

  override def sampleResource(resourceName: String, labels: Map[String, String]): Job =
    Job(
      metadata = Option(ObjectMeta(name = Option(resourceName), labels = Option(labels))),
      spec = Option(
        JobSpec(
          template = PodTemplateSpec(
            metadata = Option(ObjectMeta(name = Option(resourceName))),
            spec = Option(
              TestPodSpec.alpine.copy(restartPolicy = "Never".some)
            )
          )
        )
      )
    )
  val labels = Map("app" -> "test")
  override def modifyResource(resource: Job): Job = resource.copy(
    metadata = Option(ObjectMeta(name = resource.metadata.flatMap(_.name), labels = Option(labels)))
  )
  override def checkUpdated(updatedResource: Job): Unit =
    assert(labels.toSet.subsetOf(updatedResource.metadata.flatMap(_.labels).get.toSet))

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.jobs.namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, Job] =
    client.jobs.namespace(namespaceName)
}
