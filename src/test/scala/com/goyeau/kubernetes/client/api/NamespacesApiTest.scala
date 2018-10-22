package com.goyeau.kubernetes.client.api

import cats.Applicative
import cats.implicits._
import cats.effect._
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.operation.MinikubeClientProvider
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.k8s.api.core.v1.{Namespace, NamespaceList}
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s.Status
import org.http4s.client.UnexpectedStatus
import org.scalatest.{FlatSpec, Matchers, OptionValues}

import scala.concurrent.ExecutionContext

class NamespacesApiTest extends FlatSpec with Matchers with OptionValues with MinikubeClientProvider[IO] {
  import NamespacesApiTest._

  implicit lazy val timer: Timer[IO] = IO.timer(ExecutionContext.global)
  implicit lazy val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit lazy val F: ConcurrentEffect[IO] = IO.ioConcurrentEffect
  implicit lazy val logger: Logger[IO] = Slf4jLogger.unsafeCreate[IO]
  lazy val resourceName = classOf[Namespace].getSimpleName

  "create" should "create a namespace" in usingMinikube { implicit client =>
    createChecked[IO](resourceName.toLowerCase)
  }

  "createOrUpdate" should "create a namespace" in usingMinikube { implicit client =>
    for {
      namespaceName <- IO.pure(resourceName.toLowerCase)
      status <- client.namespaces.createOrUpdate(Namespace(metadata = Option(ObjectMeta(name = Option(namespaceName)))))
      _ = status shouldBe Status.Created
      _ <- getChecked(namespaceName)
    } yield ()
  }

  it should "update a namespace already created" in usingMinikube { implicit client =>
    for {
      namespaceName <- IO.pure(resourceName.toLowerCase)
      namespace <- createChecked(namespaceName)

      labels = Option(Map("some-label" -> "some-value"))
      status <- client.namespaces.createOrUpdate(
        namespace.copy(metadata = namespace.metadata.map(_.copy(labels = labels, resourceVersion = None)))
      )
      _ = status shouldBe Status.Ok
      updatedNamespace <- getChecked(namespaceName)
      _ = updatedNamespace.metadata.value.labels shouldBe labels
    } yield ()
  }

  "list" should "list namespaces" in usingMinikube { implicit client =>
    for {
      namespaceName <- IO.pure(resourceName.toLowerCase)
      _ <- createChecked(namespaceName)
      _ <- listChecked(Seq(namespaceName))
    } yield ()
  }

  "get" should "get a namespace" in usingMinikube { implicit client =>
    for {
      namespaceName <- IO.pure(resourceName.toLowerCase)
      _ <- createChecked(namespaceName)
      _ <- getChecked(namespaceName)
    } yield ()
  }

  it should "fail on non existing namespace" in intercept[UnexpectedStatus] {
    usingMinikube { implicit client =>
      getChecked[IO]("non-existing")
    }
  }

  "delete" should "delete a namespace" in usingMinikube { implicit client =>
    def checkEventuallyDeleted(namespaceName: String): IO[Unit] = {
      for {
        namespaces <- client.namespaces.list
        _ = namespaces.items.map(_.metadata.get.name.get) should not contain namespaceName
      } yield ()
    }.handleErrorWith(_ => checkEventuallyDeleted(namespaceName))

    for {
      namespaceName <- IO.pure(resourceName.toLowerCase)
      _ <- createChecked(namespaceName)
      _ <- client.namespaces.delete(namespaceName)
      _ <- checkEventuallyDeleted(namespaceName)
    } yield ()
  }

  it should "fail on non existing namespace" in usingMinikube { implicit client =>
    for {
      status <- client.namespaces.delete("non-existing")
      _ = status shouldBe Status.NotFound
    } yield ()
  }

  "deleteTerminated" should "delete namespace and block until fully deleted" in usingMinikube { implicit client =>
    for {
      namespaceName <- IO.pure(resourceName.toLowerCase)
      _ <- createChecked(namespaceName)
      _ <- client.namespaces.deleteTerminated(namespaceName)
      namespaces <- client.namespaces.list
      _ = namespaces.items.map(_.metadata.value.name.value) should not contain namespaceName
    } yield ()
  }

  it should "fail on non existing namespace" in usingMinikube { implicit client =>
    for {
      status <- client.namespaces.deleteTerminated("non-existing")
      _ = status shouldBe Status.NotFound
    } yield ()
  }

  "replace" should "replace a namespace" in usingMinikube { implicit client =>
    for {
      namespaceName <- IO.pure(resourceName.toLowerCase)
      _ <- createChecked(namespaceName)
      labels = Option(Map("some-label" -> "some-value"))
      status <- client.namespaces.replace(
        Namespace(
          metadata = Option(ObjectMeta(name = Option(namespaceName), labels = labels))
        )
      )
      _ = status shouldBe Status.Ok
      replacedNamespace <- getChecked(namespaceName)
      _ = replacedNamespace.metadata.value.labels shouldBe labels
    } yield ()
  }

  it should "fail on non existing namespace" in usingMinikube { implicit client =>
    for {
      status <- client.namespaces.replace(Namespace(metadata = Option(ObjectMeta(name = Option("non-existing")))))
      _ = status shouldBe Status.NotFound
    } yield ()
  }
}

object NamespacesApiTest extends Matchers with OptionValues {

  def createChecked[F[_]: Sync](namespaceName: String)(implicit client: KubernetesClient[F]): F[Namespace] = {
    def checkDefaultServiceAccountEventuallyCreated(namespaceName: String): F[Unit] = {
      for {
        serviceAccountName <- Applicative[F].pure("default")
        serviceAccount <- client.serviceAccounts.namespace(namespaceName).get(serviceAccountName)
        _ = serviceAccount.metadata.get.name.get shouldBe serviceAccountName
        _ = serviceAccount.secrets.toSeq.flatten should not be empty
      } yield ()
    }.handleErrorWith(_ => checkDefaultServiceAccountEventuallyCreated(namespaceName))

    for {
      status <- client.namespaces.create(Namespace(metadata = Option(ObjectMeta(name = Option(namespaceName)))))
      _ = status shouldBe Status.Created
      namespace <- getChecked(namespaceName)
      _ <- checkDefaultServiceAccountEventuallyCreated(namespaceName)
    } yield namespace
  }

  def listChecked[F[_]: Sync](namespaceNames: Seq[String])(implicit client: KubernetesClient[F]): F[NamespaceList] =
    for {
      namespaces <- client.namespaces.list
      _ = (namespaces.items.map(_.metadata.value.name.value) should contain).allElementsOf(namespaceNames)
    } yield namespaces

  def getChecked[F[_]: Sync](namespaceName: String)(implicit client: KubernetesClient[F]): F[Namespace] =
    for {
      namespace <- client.namespaces.get(namespaceName)
      _ = namespace.metadata.value.name.value shouldBe namespaceName
    } yield namespace
}
