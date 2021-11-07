package com.goyeau.kubernetes.client.api

import cats.effect._
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.operation._
import io.k8s.api.coordination.v1._
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import munit.FunSuite
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class LeasesApiTest
    extends FunSuite
    with CreatableTests[IO, Lease]
    with GettableTests[IO, Lease]
    with ListableTests[IO, Lease, LeaseList]
    with ReplaceableTests[IO, Lease]
    with DeletableTests[IO, Lease, LeaseList]
    with WatchableTests[IO, Lease]
    with ContextProvider {

  implicit lazy val F: Async[IO]       = IO.asyncForIO
  implicit lazy val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
  lazy val resourceName                = classOf[Lease].getSimpleName

  override def api(implicit client: KubernetesClient[IO]) = client.leases
  override def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]) =
    client.leases.namespace(namespaceName)

  override def sampleResource(resourceName: String, labels: Map[String, String]) = Lease(
    metadata = Option(ObjectMeta(name = Option(resourceName), labels = Option(labels))),
    spec = Option(LeaseSpec(holderIdentity = Option("holder1")))
  )
  val data = Option(Map("test" -> "updated-data"))
  override def modifyResource(resource: Lease) =
    resource.copy(
      metadata = resource.metadata.map(_.copy(name = resource.metadata.flatMap(_.name))),
      spec = Option(LeaseSpec(holderIdentity = Option("holder2")))
    )
  override def checkUpdated(updatedResource: Lease) =
    assertEquals(updatedResource.spec.flatMap(_.holderIdentity), Option("holder2"))

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.leases.namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, Lease] =
    client.leases.namespace(namespaceName)

  // update of non existing lease doesn't fail with error, but creates new resource
  override def munitTests() = super.munitTests().filterNot(_.name == s"fail on non existing $resourceName")
}
