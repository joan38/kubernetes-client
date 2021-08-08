package com.goyeau.kubernetes.client.api

import cats.effect._
import cats.syntax.option._
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.api.CustomResourceDefinitionsApiTest._
import com.goyeau.kubernetes.client.api.CustomResourcesApiTest.{CronTabResource, CronTabResourceList}
import com.goyeau.kubernetes.client.crd.{CrdContext, CustomResource, CustomResourceList}
import com.goyeau.kubernetes.client.operation._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.circe._
import io.circe.generic.semiauto._
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import munit.FunSuite
import org.http4s.Status
import cats.effect.unsafe.implicits.global

case class CronTab(cronSpec: String, image: String, replicas: Int)
object CronTab {
  implicit lazy val encoder: Encoder.AsObject[CronTab] = deriveEncoder
  implicit lazy val decoder: Decoder[CronTab]          = deriveDecoder
}
case class CronTabStatus(name: String)
object CronTabStatus {
  implicit lazy val encoder: Encoder.AsObject[CronTabStatus] = deriveEncoder
  implicit lazy val decoder: Decoder[CronTabStatus]          = deriveDecoder
}

object CustomResourcesApiTest {
  type CronTabResource     = CustomResource[CronTab, CronTabStatus]
  type CronTabResourceList = CustomResourceList[CronTab, CronTabStatus]
}

class CustomResourcesApiTest
    extends FunSuite
    with CreatableTests[IO, CronTabResource]
    with GettableTests[IO, CronTabResource]
    with ListableTests[IO, CronTabResource, CronTabResourceList]
    with ReplaceableTests[IO, CronTabResource]
    with DeletableTests[IO, CronTabResource, CronTabResourceList]
    with WatchableTests[IO, CronTabResource]
    with ContextProvider {

  implicit lazy val F: Async[IO]       = IO.asyncForIO
  implicit lazy val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
  lazy val resourceName                = classOf[CronTab].getSimpleName
  val kind                             = s"${classOf[CronTab].getSimpleName}"
  val context                          = CrdContext(group, "v1", plural(resourceName))
  val cronSpec                         = "* * * * * *"

  override def api(implicit client: KubernetesClient[IO]) = client.customResources[CronTab, CronTabStatus](context)
  override def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]) =
    client.customResources[CronTab, CronTabStatus](context).namespace(namespaceName)

  override def sampleResource(resourceName: String, labels: Map[String, String]) =
    CustomResource(
      s"$group/v1",
      kind,
      Some(ObjectMeta(name = Option(resourceName), labels = Option(labels))),
      CronTab(
        "",
        "image",
        1
      ),
      CronTabStatus("created").some
    )

  override def modifyResource(resource: CronTabResource) =
    resource.copy(spec = resource.spec.copy(cronSpec = cronSpec))
  override def checkUpdated(updatedResource: CronTabResource) = assertEquals(updatedResource.spec.cronSpec, cronSpec)

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.customResources[CronTab, CronTabStatus](context).namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, CronTabResource] =
    client.customResources[CronTab, CronTabStatus](context).namespace(namespaceName)

  override def beforeAll(): Unit = {
    super.beforeAll()
    assertEquals(
      kubernetesClient
        .use(client => client.customResourceDefinitions.create(crd(resourceName, Map.empty)))
        .unsafeRunSync(),
      Status.Created
    )
    ()
  }

  test("update custom resource status") {
    kubernetesClient
      .use { implicit client =>
        val name          = s"${resourceName.toLowerCase}-status"
        val resource      = sampleResource(name, Map.empty)
        val namespaceName = resourceName.toLowerCase

        for {
          status <- namespacedApi(namespaceName).create(resource)
          _ = assertEquals(status, Status.Created)
          created <- getChecked(namespaceName, name)
          updateStatus <- namespacedApi(namespaceName).updateStatus(
            name,
            created.copy(status = CronTabStatus("updated").some)
          )
          _ = assertEquals(updateStatus, Status.Ok)
          updated <- getChecked(namespaceName, name)
          _ = assertEquals(updated.status, CronTabStatus("updated").some)
        } yield ()
      }
      .unsafeRunSync()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    assertEquals(
      kubernetesClient
        .use(client => client.customResourceDefinitions.delete(crdName(resourceName)))
        .unsafeRunSync(),
      Status.Ok
    )
    ()
  }
}
