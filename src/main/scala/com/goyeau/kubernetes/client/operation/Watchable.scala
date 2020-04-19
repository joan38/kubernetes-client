package com.goyeau.kubernetes.client.operation

import cats.effect.Sync
import cats.syntax.either._
import com.goyeau.kubernetes.client.{KubeConfig, WatchEvent}
import fs2.Stream
import io.circe.jawn.CirceSupportParser
import io.circe.{Decoder, Json}
import jawnfs2._
import org.http4s.Method._
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.typelevel.jawn.Facade

private[client] trait Watchable[F[_], Resource] extends Http4sClientDsl[F] {
  protected def httpClient: Client[F]
  implicit protected val F: Sync[F]
  protected def config: KubeConfig
  protected def resourceUri: Uri
  protected def watchResourceUri: Uri = resourceUri
  implicit protected def resourceDecoder: Decoder[Resource]

  implicit val parserFacade: Facade[Json] = new CirceSupportParser(None, false).facade

  def watch: Stream[F, Either[String, WatchEvent[Resource]]] = {
    val req = GET(config.server.resolve(watchResourceUri).+?("watch", "1"), config.authorization.toSeq: _*)
    jsonStream(req).map(_.as[WatchEvent[Resource]].leftMap(_.getMessage))
  }

  private def jsonStream(req: F[Request[F]]): Stream[F, Json] =
    for {
      request <- Stream.eval(req)
      json    <- httpClient.stream(request).flatMap(_.body.chunks.parseJsonStream)
    } yield json
}
