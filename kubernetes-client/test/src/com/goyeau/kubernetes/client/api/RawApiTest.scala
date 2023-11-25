package com.goyeau.kubernetes.client.api

import cats.syntax.all.*
import cats.effect.unsafe.implicits.global
import cats.effect.{Async, IO}
import com.goyeau.kubernetes.client.KubernetesClient
import com.goyeau.kubernetes.client.Utils.retry
import com.goyeau.kubernetes.client.api.ExecStream.{StdErr, StdOut}
import com.goyeau.kubernetes.client.operation.*
import fs2.io.file.{Files, Path}
import fs2.{text, Stream}
import io.k8s.api.core.v1.*
import io.k8s.apimachinery.pkg.apis.meta.v1
import io.k8s.apimachinery.pkg.apis.meta.v1.{ListMeta, ObjectMeta}
import munit.FunSuite
import org.http4s.{Request, Status, Uri}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.nio.file.Files as JFiles
import org.http4s.implicits.*

class RawApiTest extends FunSuite with MinikubeClientProvider[IO] with ContextProvider {

  implicit override lazy val F: Async[IO]       = IO.asyncForIO
  implicit override lazy val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  // MinikubeClientProvider will create a namespace with this name, even though it's not used in this test
  override lazy val resourceName: String = "raw-api-tests"

  test("list nodes with raw requests") {
    kubernetesClient
      .use { implicit client =>
        for {
          response <- client.raw
            .runRequest(
              Request[IO](
                uri = uri"/api" / "v1" / "nodes"
              )
            )
            .use { response =>
              response.bodyText.foldMonoid.compile.lastOrError.map { body =>
                (response.status, body)
              }
            }
          (status, body) = response
          _ = assertEquals(
            status,
            Status.Ok,
            s"non 200 status for get nodes raw request"
          )
          nodeList <- F.fromEither(
            io.circe.parser.decode[NodeList](body)
          )
          _ = assert(
            nodeList.kind.contains("NodeList"),
            "wrong .kind in the response"
          )
          _ = assert(
            nodeList.items.nonEmpty,
            "empty node list"
          )
        } yield ()
      }
      .unsafeRunSync()
  }

}
