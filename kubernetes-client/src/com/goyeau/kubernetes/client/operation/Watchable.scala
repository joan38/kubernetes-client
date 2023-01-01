package com.goyeau.kubernetes.client.operation

import cats.effect.Async
import cats.syntax.either._
import com.goyeau.kubernetes.client.util.CachedExecToken
import com.goyeau.kubernetes.client.util.Uris.addLabels
import com.goyeau.kubernetes.client.{KubeConfig, WatchEvent}
import fs2.Stream
import io.circe.jawn.CirceSupportParser
import io.circe.{Decoder, Json}
import org.typelevel.jawn.fs2._
import org.http4s.Method._
import org.http4s._
import org.http4s.client.Client
import org.typelevel.jawn.Facade

private[client] trait Watchable[F[_], Resource] {
  protected def httpClient: Client[F]
  implicit protected val F: Async[F]
  protected def config: KubeConfig[F]
  protected def cachedExecToken: Option[CachedExecToken[F]]
  protected def resourceUri: Uri
  protected def watchResourceUri: Uri = resourceUri
  implicit protected def resourceDecoder: Decoder[Resource]

  implicit val parserFacade: Facade[Json] = new CirceSupportParser(None, false).facade

  def watch(labels: Map[String, String] = Map.empty): Stream[F, Either[String, WatchEvent[Resource]]] = {
    val uri = addLabels(labels, config.server.resolve(watchResourceUri))
    val req = Request[F](GET, uri.withQueryParam("watch", "1"))
      .withOptionalAuthorization(config.authorization, cachedExecToken)
    jsonStream(req).map(_.as[WatchEvent[Resource]].leftMap(_.getMessage))
  }

  private def jsonStream(req: F[Request[F]]): Stream[F, Json] =
    Stream.eval(req).flatMap(httpClient.stream).flatMap(_.body.chunks.parseJsonStream)
}
