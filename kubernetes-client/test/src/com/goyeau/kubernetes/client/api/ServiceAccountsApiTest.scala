package com.goyeau.kubernetes.client.api

import cats.effect.*
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.operation.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.k8s.api.core.v1.*
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import munit.FunSuite

class ServiceAccountsApiTest
    extends FunSuite
    with CreatableTests[IO, ServiceAccount]
    with GettableTests[IO, ServiceAccount]
    with ListableTests[IO, ServiceAccount, ServiceAccountList]
    with ReplaceableTests[IO, ServiceAccount]
    with DeletableTests[IO, ServiceAccount, ServiceAccountList]
    with WatchableTests[IO, ServiceAccount]
    with ContextProvider {

  implicit override lazy val F: Async[IO]       = IO.asyncForIO
  implicit override lazy val logger: Logger[IO] = Slf4jLogger.getLogger[IO]
  override lazy val resourceName: String        = classOf[ServiceAccount].getSimpleName

  override def api(implicit client: KubernetesClient[IO]): ServiceAccountsApi[IO] = client.serviceAccounts
  override def namespacedApi(namespaceName: String)(implicit
      client: KubernetesClient[IO]
  ): NamespacedServiceAccountsApi[IO] =
    client.serviceAccounts.namespace(namespaceName)

  override def sampleResource(resourceName: String, labels: Map[String, String]): ServiceAccount = ServiceAccount(
    metadata = Option(ObjectMeta(name = Option(resourceName), labels = Option(labels)))
  )

  private val labels                                                    = Option(Map("test" -> "updated-label"))
  override def modifyResource(resource: ServiceAccount): ServiceAccount =
    resource.copy(metadata = Option(ObjectMeta(name = resource.metadata.flatMap(_.name), labels = labels)))

  override def checkUpdated(updatedResource: ServiceAccount): Unit =
    assertEquals(updatedResource.metadata.flatMap(_.labels), labels)

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.serviceAccounts.namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, ServiceAccount] =
    client.serviceAccounts.namespace(namespaceName)
}
