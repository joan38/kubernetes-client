package com.goyeau.kubernetes.client.api

import com.goyeau.kubernetes.client.MinikubeClientProvider
import cats.effect.*
import io.k8s.api.core.v1.*
import org.http4s.{Request, Status}
import org.typelevel.log4cats.Logger
import com.goyeau.kubernetes.client.TestPlatformSpecific

import org.http4s.implicits.*

class RawApiTest extends MinikubeClientProvider  {

  implicit override lazy val logger: Logger[IO] = TestPlatformSpecific.getLogger

  // MinikubeClientProvider will create a namespace with this name, even though it's not used in this test
  override lazy val resourceName: String = "raw-api-tests"

  test("list nodes with raw requests") {
    usingMinikube { implicit client =>
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
          nodeList <- IO.fromEither(
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

  }

}
