package com.goyeau.kubernetes.client.api

import cats.effect.{ConcurrentEffect, IO}
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.operation._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.k8s.api.core.v1.{Secret, SecretList}
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import java.util.Base64
import munit.FunSuite
import org.http4s.Status
import scala.collection.compat._

class SecretsApiTest
    extends FunSuite
    with CreatableTests[IO, Secret]
    with GettableTests[IO, Secret]
    with ListableTests[IO, Secret, SecretList]
    with ReplaceableTests[IO, Secret]
    with DeletableTests[IO, Secret, SecretList]
    with WatchableTests[IO, Secret]
    with ContextProvider {

  implicit lazy val F: ConcurrentEffect[IO] = IO.ioConcurrentEffect
  implicit lazy val logger: Logger[IO]      = Slf4jLogger.getLogger[IO]
  lazy val resourceName                     = classOf[Secret].getSimpleName

  override def api(implicit client: KubernetesClient[IO]) = client.secrets
  override def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]) =
    client.secrets.namespace(namespaceName)

  override def sampleResource(resourceName: String, labels: Map[String, String]) =
    Secret(
      metadata = Option(ObjectMeta(name = Option(resourceName), labels = Option(labels))),
      data = Option(Map("test" -> "ZGF0YQ=="))
    )
  val data = Option(Map("test" -> "dXBkYXRlZC1kYXRh"))
  override def modifyResource(resource: Secret) =
    resource.copy(metadata = Option(ObjectMeta(name = resource.metadata.flatMap(_.name))), data = data)
  override def checkUpdated(updatedResource: Secret) = assertEquals(updatedResource.data, data)

  def createEncodeChecked(namespaceName: String, secretName: String)(implicit
      client: KubernetesClient[IO]
  ): IO[Secret] =
    for {
      _ <- NamespacesApiTest.createChecked[IO](namespaceName)
      data = Map("test" -> "data")
      status <-
        client.secrets
          .namespace(namespaceName)
          .createEncode(
            Secret(
              metadata = Option(ObjectMeta(name = Option(secretName))),
              data = Option(Map("test" -> "data"))
            )
          )
      _ = assertEquals(status, Status.Created)
      secret <- getChecked(namespaceName, secretName)
      _ = assertEquals(secret.data.get.values.head, Base64.getEncoder.encodeToString(data.values.head.getBytes))
    } yield secret

  test("createEncode should create a secret") {
    usingMinikube { implicit client =>
      val namespaceName = resourceName.toLowerCase + "-create-encode"
      createEncodeChecked(namespaceName, "some-secret").guarantee(client.namespaces.delete(namespaceName).void)
    }
  }

  test("createOrUpdateEncode should create a secret") {
    usingMinikube { implicit client =>
      val namespaceName = resourceName.toLowerCase + "-create-update-encode"
      (for {
        _ <- NamespacesApiTest.createChecked(namespaceName)

        secretName = "some-secret"
        status <-
          client.secrets
            .namespace(namespaceName)
            .createOrUpdateEncode(
              Secret(
                metadata = Option(ObjectMeta(name = Option(secretName))),
                data = Option(Map("test" -> "data"))
              )
            )
        _ = assertEquals(status, Status.Created)
        _ <- getChecked(namespaceName, secretName)
      } yield ()).guarantee(client.namespaces.delete(namespaceName).void)
    }
  }

  test("update a secret already created") {
    usingMinikube { implicit client =>
      val namespaceName = resourceName.toLowerCase + "-update-encode"
      (for {
        secretName <- IO.pure("some-secret")
        secret     <- createEncodeChecked(namespaceName, secretName)

        data = Option(Map("test" -> "updated-data"))
        status <-
          client.secrets
            .namespace(namespaceName)
            .createOrUpdateEncode(
              secret.copy(
                metadata = Option(ObjectMeta(name = secret.metadata.flatMap(_.name))),
                data = data
              )
            )
        _ = assertEquals(status, Status.Ok)
        updatedSecret <- getChecked(namespaceName, secretName)
        _ = assertEquals(
          updatedSecret.data,
          data.map(
            _.view.mapValues(v => Base64.getEncoder.encodeToString(v.getBytes)).toMap
          )
        )
      } yield ()).guarantee(client.namespaces.delete(namespaceName).void)
    }
  }

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.secrets.namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, Secret] =
    client.secrets.namespace(namespaceName)
}
