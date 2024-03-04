package com.goyeau.kubernetes.client.api

import cats.syntax.all.*
import cats.effect.*
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.MinikubeClientProvider
import io.k8s.api.core.v1.Node
import org.typelevel.log4cats.Logger
import com.goyeau.kubernetes.client.TestPlatformSpecific

class NodesApiTest extends MinikubeClientProvider {

  implicit lazy val logger: Logger[IO]                         = TestPlatformSpecific.getLogger
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
