package com.goyeau.kubernetes.client

import cats.syntax.all.*
import cats.effect.syntax.all.*
import cats.effect.*
import fs2.io.net.Network
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.jdkhttpclient.{JdkHttpClient, JdkWSClient}
import java.net.http.HttpClient

private object PlatformSpecific {

  def clients[F[_]: Async: Network](config: KubeConfig[F]): Resource[F, Clients[F]] =
    jdk(config)

  private def ember[F[_]: Async: Network](config: KubeConfig[F]): Resource[F, Clients[F]] =
    for {
      tlsContext <- TlsContexts.fromConfig(config).toResource
      builderRaw = EmberClientBuilder.default[F]
      builder    = tlsContext.fold(builderRaw)(builderRaw.withTLSContext)
      clients <- builder.buildWebSocket
      (http, ws) = clients
    } yield Clients(http, ws)

  private def jdk[F[_]: Async](config: KubeConfig[F]): Resource[F, Clients[F]] =
    Resource.eval {
      for {
        client     <- Async[F].delay(HttpClient.newBuilder().sslContext(SslContexts.fromConfig(config)).build())
        httpClient <- Async[F].delay(JdkHttpClient[F](client))
        wsClient   <- Async[F].delay(JdkWSClient[F](client))
      } yield Clients(httpClient, wsClient)
    }

}
