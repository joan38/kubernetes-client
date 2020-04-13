package com.goyeau.kubernetes.client.operation

import cats.effect.Sync
import cats.syntax.either._
import com.goyeau.kubernetes.client.{KubeConfig, WatchEvent}
import io.circe.{Decoder, Json}
import jawnfs2._
import org.http4s.Method._
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl

private[client] trait Watchable[F[_], Resource] extends Http4sClientDsl[F] {
  protected def httpClient: Client[F]
  implicit protected val F: Sync[F]
  protected def config: KubeConfig
  protected def resourceUri: Uri
  implicit protected def resourceDecoder: Decoder[Resource]

  implicit val f = new io.circe.jawn.CirceSupportParser(None, false).facade

  def watch: fs2.Stream[F, Either[String, WatchEvent[Resource]]] = {
    val req = GET(config.server.resolve(resourceUri).+?("watch", "1"), config.authorization.toSeq: _*)
    val s   = jsonStream(req)
    s.map(_.as[WatchEvent[Resource]].leftMap(_.getMessage))
  }

  private def jsonStream(req: F[Request[F]]): fs2.Stream[F, Json] =
    for {
      r   <- fs2.Stream.eval(req)
      res <- httpClient.stream(r).flatMap(_.body.chunks.parseJsonStream)
    } yield res
}
