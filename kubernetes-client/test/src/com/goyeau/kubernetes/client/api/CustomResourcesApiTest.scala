package com.goyeau.kubernetes.client.api

import cats.effect.*
import cats.implicits.*
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.api.CustomResourceDefinitionsApiTest.*
import com.goyeau.kubernetes.client.api.CustomResourcesApiTest.{CronTabResource, CronTabResourceList}
import com.goyeau.kubernetes.client.crd.{CrdContext, CustomResource, CustomResourceList}
import com.goyeau.kubernetes.client.operation.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.circe.*
import io.circe.generic.semiauto.*
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import munit.FunSuite
import org.http4s.Status

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

  implicit override lazy val F: Async[IO]       = IO.asyncForIO
  implicit override lazy val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
  override lazy val resourceName: String        = classOf[CronTab].getSimpleName
  val kind                                      = classOf[CronTab].getSimpleName
  val context                                   = CrdContext(group, "v1", plural(resourceName))
  val cronSpec                                  = "* * * * * *"
  val crLabels                                  = Map("it-tests" -> "true")

  override def api(implicit client: KubernetesClient[IO]): CustomResourcesApi[IO, CronTab, CronTabStatus] =
    client.customResources[CronTab, CronTabStatus](context)

  override def namespacedApi(
      namespaceName: String
  )(implicit
      client: KubernetesClient[IO]
  ): NamespacedCustomResourcesApi[IO, CronTab, CronTabStatus] =
    client.customResources[CronTab, CronTabStatus](context).namespace(namespaceName)

  override def sampleResource(resourceName: String, labels: Map[String, String]): CronTabResource =
    CustomResource(
      s"$group/v1",
      kind,
      Some(ObjectMeta(name = Option(resourceName), labels = Option(labels ++ crLabels))),
      CronTab(
        "",
        "image",
        1
      ),
      CronTabStatus("created").some
    )

  override def modifyResource(resource: CronTabResource): CronTabResource =
    resource.copy(spec = resource.spec.copy(cronSpec = cronSpec))
  override def checkUpdated(updatedResource: CronTabResource): Unit =
    assertEquals(updatedResource.spec.cronSpec, cronSpec)

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.customResources[CronTab, CronTabStatus](context).namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, CronTabResource] =
    client.customResources[CronTab, CronTabStatus](context).namespace(namespaceName)

  test("update custom resource status") {
    usingMinikube { implicit client =>
      val name          = s"${resourceName.toLowerCase}-status"
      val resource      = sampleResource(name, Map.empty)
      val namespaceName = resourceName.toLowerCase

      for {
        _      <- CustomResourceDefinitionsApiTest.getChecked(resourceName)
        status <- namespacedApi(namespaceName).create(resource)
        _ = assertEquals(status, Status.Created, status.sanitizedReason)

        created <- getChecked(namespaceName, name)
        updateStatus <- namespacedApi(namespaceName).updateStatus(
          name,
          created.copy(status = CronTabStatus("updated").some)
        )
        _ = assertEquals(updateStatus, Status.Ok, updateStatus.sanitizedReason)
        updated <- getChecked(namespaceName, name)
        _ = assertEquals(updated.status, CronTabStatus("updated").some)
      } yield ()
    }
  }

  override def beforeAll(): Unit = {
    createNamespaces()

    usingMinikube(implicit client =>
      client.customResourceDefinitions.deleteTerminated(resourceName) *> CustomResourceDefinitionsApiTest
        .getChecked(
          resourceName
        )
        .recoverWith { case _ =>
          logger.info(s"CRD '$resourceName' is not there, creating it.") *>
            CustomResourceDefinitionsApiTest
              .createChecked(resourceName, crLabels)
        }
        .void
    )
  }

  override def afterAll(): Unit = {
    usingMinikube { implicit client =>
      val namespaces = extraNamespace :+ defaultNamespace
      for {
        deleteStatus <- namespaces.traverse(ns => namespacedApi(ns).deleteAll(crLabels))
        _ = deleteStatus.foreach(s => assertEquals(s, Status.Ok))
        _ <- logger.info(s"CRDs with label $crLabels were deleted in $namespaces namespace(s).")
      } yield ()
    }
    super.afterAll()
  }
}
