package com.goyeau.kubernetes.client.api

import cats.effect.syntax.all.*
import cats.effect.{Async, Resource}
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation.*
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.jdkhttpclient.{WSClient, WSConnectionHighLevel, WSRequest}
import org.http4s.{Request, Response}

private[client] class RawApi[F[_]](
    httpClient: Client[F],
    wsClient: WSClient[F],
    config: KubeConfig[F],
    authorization: Option[F[Authorization]]
)(implicit F: Async[F]) {

  def runRequest(
      request: Request[F]
  ): Resource[F, Response[F]] =
    Request[F](
      method = request.method,
      uri = config.server.resolve(request.uri),
      httpVersion = request.httpVersion,
      headers = request.headers,
      body = request.body,
      attributes = request.attributes
    ).withOptionalAuthorization(authorization)
      .toResource
      .flatMap(httpClient.run)

  def connectWS(
      request: WSRequest
  ): Resource[F, WSConnectionHighLevel[F]] =
    request
      .copy(uri = config.server.resolve(request.uri))
      .withOptionalAuthorization(authorization)
      .toResource
      .flatMap { request =>
        wsClient.connectHighLevel(request)
      }

}
