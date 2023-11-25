package com.goyeau.kubernetes.client.api

import cats.effect.*
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.MinikubeClientProvider
import com.goyeau.kubernetes.client.Utils.*
import org.typelevel.log4cats.Logger
import com.goyeau.kubernetes.client.TestPlatformSpecific
import io.k8s.api.core.v1.{Namespace, NamespaceList}
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s.Status
import org.http4s.client.UnexpectedStatus
import munit.Assertions.*

class NamespacesApiTest extends MinikubeClientProvider  {
  import NamespacesApiTest.*

  implicit lazy val logger: Logger[IO] = TestPlatformSpecific.getLogger
  lazy val resourceName: String        = classOf[Namespace].getSimpleName

  test("create a namespace") {
    usingMinikube { implicit client =>
      val namespaceName = s"${resourceName.toLowerCase}-ns-create"
      createChecked(namespaceName).guarantee(client.namespaces.delete(namespaceName).void)
    }
  }

  test("create a namespace") {
    usingMinikube { implicit client =>
      val namespaceName = resourceName.toLowerCase + "-create-update"
      (for {
        status <-
          client.namespaces.createOrUpdate(Namespace(metadata = Option(ObjectMeta(name = Option(namespaceName)))))
        _ = assertEquals(status, Status.Created)
        _ <- getChecked(namespaceName)
      } yield ()).guarantee(client.namespaces.delete(namespaceName).void)
    }
  }

  test("update a namespace already created") {
    usingMinikube { implicit client =>
      val namespaceName = resourceName.toLowerCase + "-update"
      (for {
        namespace <- createChecked(namespaceName)

        labels = Option(Map("some-label" -> "some-value"))
        status <- client.namespaces.createOrUpdate(
          namespace.copy(metadata = namespace.metadata.map(_.copy(labels = labels, resourceVersion = None)))
        )
        _ = assertEquals(status, Status.Ok)
        updatedNamespace <- getChecked(namespaceName)
        _ = assert(updatedNamespace.metadata.flatMap(_.labels).exists(l => labels.get.toSet.subsetOf(l.toSet)))
      } yield ()).guarantee(client.namespaces.delete(namespaceName).void)
    }
  }

  test("list namespaces") {
    usingMinikube { implicit client =>
      val namespaceName = resourceName.toLowerCase + "-list"
      (for {
        _ <- createChecked(namespaceName)
        _ <- listChecked(Seq(namespaceName))
      } yield ()).guarantee(client.namespaces.delete(namespaceName).void)
    }
  }

  test("get a namespace") {
    usingMinikube { implicit client =>
      val namespaceName = resourceName.toLowerCase + "-get"
      (for {
        _ <- createChecked(namespaceName)
        _ <- getChecked(namespaceName)
      } yield ()).guarantee(client.namespaces.delete(namespaceName).void)
    }
  }

  test("get a namespace fail on non existing namespace") {
    intercept[UnexpectedStatus] {
      usingMinikube(implicit client => getChecked("non-existing"))
    }
  }

  test("delete a namespace") {
    usingMinikube { implicit client =>
      for {
        namespaceName <- IO.pure(resourceName.toLowerCase + "-delete")
        _             <- createChecked(namespaceName)
        _             <- client.namespaces.delete(namespaceName)
        _ <- retry(
          for {
            namespaces <- client.namespaces.list()
            _ = assert(!namespaces.items.flatMap(_.metadata).flatMap(_.name).contains(namespaceName))
          } yield (),
          actionClue = Some(s"Namespace deletion: $namespaceName")
        )
      } yield ()
    }
  }

  test("delete a namespace should fail on non existing namespace") {
    usingMinikube { implicit client =>
      for {
        status <- client.namespaces.delete("non-existing")
        _ = assertEquals(status, Status.NotFound)
      } yield ()
    }
  }

  test("delete namespace and block until fully deleted") {
    usingMinikube { implicit client =>
      for {
        namespaceName <- IO.pure(resourceName.toLowerCase + "-delete-terminated")
        _             <- createChecked(namespaceName)
        _             <- client.namespaces.deleteTerminated(namespaceName)
        namespaces    <- client.namespaces.list()
        _ = assert(!namespaces.items.flatMap(_.metadata).flatMap(_.name).contains(namespaceName))
      } yield ()
    }
  }

  test("deleteTerminated a namespace should fail on non existing namespace") {
    usingMinikube { implicit client =>
      for {
        status <- client.namespaces.deleteTerminated("non-existing")
        _ = assertEquals(status, Status.NotFound)
      } yield ()
    }
  }

  test("replace a namespace") {
    usingMinikube { implicit client =>
      val namespaceName = resourceName.toLowerCase + "-replace"
      (for {
        _ <- createChecked(namespaceName)
        labels = Option(Map("some-label" -> "some-value"))
        status <- client.namespaces.replace(
          Namespace(metadata = Option(ObjectMeta(name = Option(namespaceName), labels = labels)))
        )
        _ = assertEquals(status, Status.Ok)
        replacedNamespace <- getChecked(namespaceName)
        _ = assert(replacedNamespace.metadata.flatMap(_.labels).exists(l => labels.get.toSet.subsetOf(l.toSet)))
      } yield ()).guarantee(client.namespaces.delete(namespaceName).void)
    }
  }

  test("replace a namespace should fail on non existing namespace") {
    usingMinikube { implicit client =>
      for {
        status <- client.namespaces.replace(Namespace(metadata = Option(ObjectMeta(name = Option("non-existing")))))
        _ = assertEquals(status, Status.NotFound)
      } yield ()
    }
  }
}

object NamespacesApiTest {

  def createChecked(
      namespaceName: String
  )(implicit client: KubernetesClient[IO], logger: Logger[IO]): IO[Namespace] =
    for {
      status <- client.namespaces.create(Namespace(metadata = Option(ObjectMeta(name = Option(namespaceName)))))
      _ = assertEquals(status, Status.Created, s"Namespace '$namespaceName' creation failed.")
      namespace <- retry(getChecked(namespaceName))
      serviceAccountName = "default"
      _ <- retry(
        for {
          serviceAccount <- client.serviceAccounts.namespace(namespaceName).get(serviceAccountName)
          _ = assertEquals(serviceAccount.metadata.flatMap(_.name), Some(serviceAccountName))
        } yield (),
        actionClue = Some(s"Namespace creation: $namespaceName")
      )
    } yield namespace

  def listChecked(namespaceNames: Seq[String])(implicit client: KubernetesClient[IO]): IO[NamespaceList] =
    for {
      namespaces <- client.namespaces.list()
      _ = assert(namespaceNames.toSet.subsetOf(namespaces.items.flatMap(_.metadata).flatMap(_.name).toSet))
    } yield namespaces

  def getChecked(namespaceName: String)(implicit client: KubernetesClient[IO]): IO[Namespace] =
    for {
      namespace <- client.namespaces.get(namespaceName)
      _ = assertEquals(namespace.metadata.flatMap(_.name), Some(namespaceName))
    } yield namespace
}
