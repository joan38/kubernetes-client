package com.goyeau.kubernetes.client.api

import cats.effect.*
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.MinikubeClientProvider
import com.goyeau.kubernetes.client.operation.*
import io.k8s.api.coordination.v1.*
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.typelevel.log4cats.Logger
import com.goyeau.kubernetes.client.TestPlatformSpecific

class LeasesApiTest
    extends MinikubeClientProvider
    with CreatableTests[Lease]
    with GettableTests[Lease]
    with ListableTests[Lease, LeaseList]
    with ReplaceableTests[Lease]
    with DeletableTests[Lease, LeaseList]
    with WatchableTests[Lease]
     {

  implicit override lazy val logger: Logger[IO] = TestPlatformSpecific.getLogger
  override lazy val resourceName: String        = classOf[Lease].getSimpleName

  override def api(implicit client: KubernetesClient[IO]): LeasesApi[IO] = client.leases
  override def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]): NamespacedLeasesApi[IO] =
    client.leases.namespace(namespaceName)

  override def sampleResource(resourceName: String, labels: Map[String, String]): Lease = Lease(
    metadata = Option(ObjectMeta(name = Option(resourceName), labels = Option(labels))),
    spec = Option(LeaseSpec(holderIdentity = Option("holder1")))
  )

  override def modifyResource(resource: Lease): Lease =
    resource.copy(
      metadata = resource.metadata.map(_.copy(name = resource.metadata.flatMap(_.name))),
      spec = Option(LeaseSpec(holderIdentity = Option("holder2")))
    )
  override def checkUpdated(updatedResource: Lease): Unit =
    assertEquals(updatedResource.spec.flatMap(_.holderIdentity), Option("holder2"))

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.leases.namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, Lease] =
    client.leases.namespace(namespaceName)

  // update of non existing lease doesn't fail with error, but creates new resource
//  override def munitTests(): Seq[Test] = super.munitTests().filterNot(_.name == s"fail on non existing $resourceName")
}
