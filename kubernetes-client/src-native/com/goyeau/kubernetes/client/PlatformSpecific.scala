package com.goyeau.kubernetes.client

import cats.effect.*
import cats.effect.std.Env
import fs2.io.file.Files
import fs2.io.net.Network
import org.http4s.ember.client.EmberClientBuilder

private object PlatformSpecific {

  def clients[F[_]: Async: Network: Env: Files](config: KubeConfig[F]): Resource[F, Clients[F]] =
    for {
      tlsContext <- TlsContexts.fromConfig(config)
      builderRaw = EmberClientBuilder.default[F]
      builder = tlsContext.fold(builderRaw) { ctx =>
        builderRaw.withTLSContext(ctx)
      }
      clients <- builder.buildWebSocket
      (http, ws) = clients
    } yield Clients(http, ws)

}
