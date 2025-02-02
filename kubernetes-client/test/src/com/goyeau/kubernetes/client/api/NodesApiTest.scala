package com.goyeau.kubernetes.client.api

import cats.effect.*
import cats.implicits.*
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.operation.*
import io.k8s.api.core.v1.Node
import munit.FunSuite
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class NodesApiTest extends FunSuite with ContextProvider with MinikubeClientProvider[IO] {
  implicit lazy val F: Async[IO]                               = IO.asyncForIO
  implicit lazy val logger: Logger[IO]                         = Slf4jLogger.getLogger[IO]
  lazy val resourceName: String                                = classOf[Node].getSimpleName
  def api(implicit client: KubernetesClient[IO]): NodesApi[IO] = client.nodes

  def getChecked(resourceName: String)(implicit client: KubernetesClient[IO]): IO[Node] =
    for {
      resource <- api.get(resourceName)
      _ = assertEquals(resource.metadata.flatMap(_.name), Some(resourceName))
    } yield resource

  test("lists nodes and gets details of first on the list") {
    usingMinikube { implicit client =>
      for {
        headOption <- api.list().map(_.items.headOption)
        _          <- headOption.traverse(h => getChecked(h.metadata.flatMap(_.name).get))
      } yield ()
    }
  }

  test("cannot get non existing resource") {
    usingMinikube { implicit client =>
      for {
        resourceAttempt <- getChecked("non-existing").attempt
        _ = assert(resourceAttempt.isLeft)
      } yield ()
    }
  }
}
