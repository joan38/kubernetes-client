package com.goyeau.kubernetes.client

import cats.syntax.all.*
import cats.effect.*
import cats.effect.std.Env
import cats.effect.syntax.all.*
import fs2.io.net.Network
import fs2.io.file.Files
import fs2.io.process.Processes
import org.typelevel.log4cats.Logger
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.jdkhttpclient.{JdkHttpClient, JdkWSClient}
import java.net.http.HttpClient

trait PlatformSpecific {
  self: KubernetesClient.type =>

  def jdk[F[_]: Async: Logger: Files: Network: Processes: Env](
      config: KubeConfig[F]
  ): Resource[F, KubernetesClient[F]] =
    create(config, PlatformSpecific.jdk(_))

  def jdk[F[_]: Async: Files: Logger: Network: Processes: Env](
      config: F[KubeConfig[F]]
  ): Resource[F, KubernetesClient[F]] =
    Resource.eval(config).flatMap(jdk(_))

}

private object PlatformSpecific {

  def ember[F[_]: Async: Network](config: KubeConfig[F]): Resource[F, Clients[F]] =
    for {
      tlsContext <- TlsContexts.fromConfig(config).toResource
      builderRaw = EmberClientBuilder.default[F]
      builder    = tlsContext.fold(builderRaw)(builderRaw.withTLSContext)
      clients <- builder.buildWebSocket
      (http, ws) = clients
    } yield Clients(http, ws)

  def jdk[F[_]: Async](config: KubeConfig[F]): Resource[F, Clients[F]] =
    Resource.eval {
      for {
        sslContext <- SslContexts.fromConfig(config)
        client     <- Async[F].delay(HttpClient.newBuilder().sslContext(sslContext).build())
        httpClient <- Async[F].delay(JdkHttpClient[F](client))
        wsClient   <- Async[F].delay(JdkWSClient[F](client))
      } yield Clients(httpClient, wsClient)
    }

}
