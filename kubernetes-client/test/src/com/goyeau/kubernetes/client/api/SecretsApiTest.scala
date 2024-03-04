package com.goyeau.kubernetes.client.api

import cats.syntax.all.*
import cats.effect.*
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.MinikubeClientProvider
import com.goyeau.kubernetes.client.operation.*
import org.typelevel.log4cats.Logger
import com.goyeau.kubernetes.client.TestPlatformSpecific
import io.k8s.api.core.v1.{Secret, SecretList}
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s.Status

class SecretsApiTest
    extends MinikubeClientProvider
    with CreatableTests[Secret]
    with GettableTests[Secret]
    with ListableTests[Secret, SecretList]
    with ReplaceableTests[Secret]
    with DeletableTests[Secret, SecretList]
    with WatchableTests[Secret]
     {

  implicit override lazy val logger: Logger[IO] = TestPlatformSpecific.getLogger
  override lazy val resourceName: String        = classOf[Secret].getSimpleName

  override def api(implicit client: KubernetesClient[IO]): SecretsApi[IO] = client.secrets
  override def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]): NamespacedSecretsApi[IO] =
    client.secrets.namespace(namespaceName)

  override def sampleResource(resourceName: String, labels: Map[String, String]): Secret =
    Secret(
      metadata = Option(ObjectMeta(name = Option(resourceName), labels = Option(labels))),
      data = Option(Map("test" -> "ZGF0YQ=="))
    )

  private val data = Option(Map("test" -> "dXBkYXRlZC1kYXRh"))
  override def modifyResource(resource: Secret): Secret =
    resource.copy(metadata = Option(ObjectMeta(name = resource.metadata.flatMap(_.name))), data = data)
  override def checkUpdated(updatedResource: Secret): Unit = assertEquals(updatedResource.data, data)

  def createEncodeChecked(namespaceName: String, secretName: String)(implicit
      client: KubernetesClient[IO]
  ): IO[Secret] =
    for {
      _ <- NamespacesApiTest.createChecked(namespaceName)
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
      encoded <- fs2.Stream.emit(data.values.head).covary[IO]
      .through(fs2.text.utf8.encode)
      .through(fs2.text.base64.encode)
      .foldMonoid
      .compile
      .lastOrError
      _ = assertEquals(secret.data.get.values.head, encoded)
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
        encoded <- data.traverse { data => 
          data.view.toSeq.traverse { case (k, v) => 
            fs2.Stream.emit(v).covary[IO]
            .through(fs2.text.utf8.encode)
            .through(fs2.text.base64.encode)
            .foldMonoid
            .compile
            .lastOrError
            .map { encoded => k -> encoded }
            }.map(_.toMap)
          }
        _ = assertEquals(
          updatedSecret.data,
          encoded
        )
      } yield ()).guarantee(client.namespaces.delete(namespaceName).void)
    }
  }

  override def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO] =
    client.secrets.namespace(namespaceName)

  override def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, Secret] =
    client.secrets.namespace(namespaceName)
}
