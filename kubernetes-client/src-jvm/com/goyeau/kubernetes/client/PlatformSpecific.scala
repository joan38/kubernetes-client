package com.goyeau.kubernetes.client

import cats.syntax.all.*
import cats.effect.*
import cats.effect.std.Env
import fs2.io.net.Network
import fs2.io.file.Files
import fs2.io.process.Processes
import org.typelevel.log4cats.Logger
import org.http4s.jdkhttpclient.{JdkHttpClient, JdkWSClient}
import java.net.http.HttpClient

trait PlatformSpecific {
  self: KubernetesClient.type =>

  def jdk[F[_]: Async: Logger: Files: Network: Processes: Env](
      config: KubeConfig[F],
      adaptClients: Clients[F] => Resource[F, Clients[F]]
  ): Resource[F, KubernetesClient[F]] =
    create(config, PlatformSpecific.jdkClients(_), adaptClients)

  def jdk[F[_]: Async: Logger: Files: Network: Processes: Env](
      config: KubeConfig[F]
  ): Resource[F, KubernetesClient[F]] =
    create(config, PlatformSpecific.jdkClients(_), noAdapt[F])

  def jdk[F[_]: Async: Files: Logger: Network: Processes: Env](
      config: F[KubeConfig[F]],
      adaptClients: Clients[F] => Resource[F, Clients[F]]
  ): Resource[F, KubernetesClient[F]] =
    Resource.eval(config).flatMap(create(_, PlatformSpecific.jdkClients(_), adaptClients))

  def jdk[F[_]: Async: Files: Logger: Network: Processes: Env](
      config: F[KubeConfig[F]]
  ): Resource[F, KubernetesClient[F]] =
    Resource.eval(config).flatMap(create(_, PlatformSpecific.jdkClients(_), noAdapt[F]))

}

private object PlatformSpecific {

  def jdkClients[F[_]: Async](config: KubeConfig[F]): Resource[F, Clients[F]] =
    Resource.eval {
      for {
        sslContext <- SslContexts.fromConfig(config)
        client     <- Async[F].delay(HttpClient.newBuilder().sslContext(sslContext).build())
        httpClient <- Async[F].delay(JdkHttpClient[F](client))
        wsClient   <- Async[F].delay(JdkWSClient[F](client))
      } yield Clients(httpClient, wsClient)
    }

}
