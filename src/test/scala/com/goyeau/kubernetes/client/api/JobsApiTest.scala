package com.goyeau.kubernetes.client.api

import cats.effect.{ConcurrentEffect, ContextShift, IO, Timer}
import com.goyeau.kubernetes.client.operation._
import com.goyeau.kubernetes.client.KubernetesClient
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.k8s.api.batch.v1.{Job, JobList, JobSpec}
import io.k8s.api.core.v1._
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.scalatest.{FlatSpec, Matchers, OptionValues}

import scala.concurrent.ExecutionContext

class JobsApiTest
    extends FlatSpec
    with Matchers
    with OptionValues
    with CreatableTests[IO, Job]
    with GettableTests[IO, Job]
    with ListableTests[IO, Job, JobList]
    with ReplaceableTests[IO, Job]
    with DeletableTests[IO, Job, JobList]
    with DeletableTerminatedTests[IO, Job, JobList] {

  implicit lazy val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  implicit lazy val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit lazy val F: ConcurrentEffect[IO] = IO.ioConcurrentEffect
  implicit lazy val logger: Logger[IO] = Slf4jLogger.unsafeCreate[IO]
  lazy val resourceName = classOf[Job].getSimpleName

  override def api(implicit client: KubernetesClient[IO]) = client.jobs
  override def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]) =
    client.jobs.namespace(namespaceName)

  override def sampleResource(resourceName: String) =
    Job(
      metadata = Option(ObjectMeta(name = Option(resourceName))),
      spec = Option(
        JobSpec(
          template = PodTemplateSpec(
            metadata = Option(ObjectMeta(name = Option(resourceName))),
            spec = Option(
              PodSpec(containers = Seq(Container("test", image = Option("docker"))), restartPolicy = Option("Never"))
            )
          )
        )
      )
    )
  val labels = Map("app" -> "test")
  override def modifyResource(resource: Job) = resource.copy(
    metadata = Option(ObjectMeta(name = resource.metadata.flatMap(_.name), labels = Option(labels)))
  )
  override def checkUpdated(updatedResource: Job) =
    (updatedResource.metadata.value.labels.value.toSeq should contain).allElementsOf(labels.toSeq)
}
