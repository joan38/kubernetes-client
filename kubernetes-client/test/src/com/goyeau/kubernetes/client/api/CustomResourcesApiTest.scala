package com.goyeau.kubernetes.client.api

import cats.effect._
import cats.syntax.option._
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.api.CustomResourceDefinitionsApiTest._
import com.goyeau.kubernetes.client.api.CustomResourcesApiTest.{CronTabResource, CronTabResourceList}
import com.goyeau.kubernetes.client.crd.{CrdContext, CustomResource, CustomResourceList}
import com.goyeau.kubernetes.client.operation._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe._
import io.circe.generic.semiauto._
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s.Status
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

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
    extends AnyFlatSpec
    with Matchers
    with OptionValues
    with CreatableTests[IO, CronTabResource]
    with GettableTests[IO, CronTabResource]
    with ListableTests[IO, CronTabResource, CronTabResourceList]
    with ReplaceableTests[IO, CronTabResource]
    with DeletableTests[IO, CronTabResource, CronTabResourceList]
    with WatchableTests[IO, CronTabResource]
    with ContextProvider {

  implicit lazy val F: ConcurrentEffect[IO] = IO.ioConcurrentEffect
  implicit lazy val logger: Logger[IO]      = Slf4jLogger.getLogger[IO]
  lazy val resourceName                     = classOf[CronTab].getSimpleName
  val kind                                  = s"${classOf[CronTab].getSimpleName}"
  val context                               = CrdContext(group, "v1", plural(resourceName))
  val cronSpec                              = "* * * * * *"

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
  override def checkUpdated(updatedResource: CronTabResource) = updatedResource.spec.cronSpec shouldBe cronSpec

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.customResources[CronTab, CronTabStatus](context).namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, CronTabResource] =
    client.customResources[CronTab, CronTabStatus](context).namespace(namespaceName)

  override def beforeAll(): Unit = {
    super.beforeAll()
    kubernetesClient
      .use(client => client.customResourceDefinitions.create(crd(resourceName, Map.empty)))
      .unsafeRunSync() should === (Status.Created)
    ()
  }

  it should "update custom resource status" in {
    kubernetesClient
      .use { implicit client =>
        val name          = s"${resourceName.toLowerCase}-status"
        val resource      = sampleResource(name, Map.empty)
        val namespaceName = resourceName.toLowerCase

        for {
          status <- namespacedApi(namespaceName).create(resource)
          _ = status shouldBe Status.Created
          created <- getChecked(namespaceName, name)
          updateStatus <- namespacedApi(namespaceName).updateStatus(
            name,
            created.copy(status = CronTabStatus("updated").some)
          )
          _ = updateStatus shouldBe Status.Ok
          updated <- getChecked(namespaceName, name)
          _ = updated.status should === (CronTabStatus("updated").some)
        } yield ()
      }
      .unsafeRunSync()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    kubernetesClient
      .use(client => client.customResourceDefinitions.delete(crdName(resourceName)))
      .unsafeRunSync() should === (Status.Ok)
    ()
  }
}
