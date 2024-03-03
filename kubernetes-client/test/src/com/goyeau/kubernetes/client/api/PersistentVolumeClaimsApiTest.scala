package com.goyeau.kubernetes.client.api

import cats.syntax.all.*
import cats.effect.*
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.operation.*
import io.k8s.api.core.v1.{
  PersistentVolumeClaim,
  PersistentVolumeClaimList,
  PersistentVolumeClaimSpec,
  ResourceRequirements
}
import io.k8s.apimachinery.pkg.api.resource.Quantity
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import munit.FunSuite
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class PersistentVolumeClaimsApiTest
    extends FunSuite
    with CreatableTests[IO, PersistentVolumeClaim]
    with GettableTests[IO, PersistentVolumeClaim]
    with ListableTests[IO, PersistentVolumeClaim, PersistentVolumeClaimList]
    with ReplaceableTests[IO, PersistentVolumeClaim]
    with DeletableTests[IO, PersistentVolumeClaim, PersistentVolumeClaimList]
    with WatchableTests[IO, PersistentVolumeClaim]
    with ContextProvider {

  implicit override lazy val F: Async[IO]       = IO.asyncForIO
  implicit override lazy val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
  override lazy val resourceName: String        = classOf[PersistentVolumeClaim].getSimpleName

  override def api(implicit client: KubernetesClient[IO]): PersistentVolumeClaimsApi[IO] = client.persistentVolumeClaims
  override def namespacedApi(namespaceName: String)(implicit
      client: KubernetesClient[IO]
  ): NamespacedPersistentVolumeClaimsApi[IO] =
    client.persistentVolumeClaims.namespace(namespaceName)

  override def sampleResource(resourceName: String, labels: Map[String, String]): PersistentVolumeClaim =
    PersistentVolumeClaim(
      metadata = ObjectMeta(name = resourceName.some, labels = labels.some).some,
      spec = PersistentVolumeClaimSpec(
        accessModes = Seq("ReadWriteOnce").some,
        resources = ResourceRequirements(
          requests = Map("storage" -> Quantity("1Mi")).some
        ).some,
        storageClassName = "local-path".some
      ).some
    )

  override def modifyResource(resource: PersistentVolumeClaim): PersistentVolumeClaim =
    resource.copy(
      metadata = resource.metadata.map(
        _.copy(
          labels = resource.metadata.flatMap(_.labels).getOrElse(Map.empty).updated("key", "value").some
        )
      )
    )

  override def checkUpdated(updatedResource: PersistentVolumeClaim): Unit =
    assertEquals(updatedResource.metadata.flatMap(_.labels).flatMap(_.get("key")), "value".some)

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.persistentVolumeClaims.namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit
      client: KubernetesClient[IO]
  ): Watchable[IO, PersistentVolumeClaim] =
    client.persistentVolumeClaims.namespace(namespaceName)

}
