package com.goyeau.kubernetes.client

import cats.effect.syntax.all.*
import cats.effect.*
import fs2.io.net.Network
import fs2.io.file.Files
import org.http4s.ember.client.EmberClientBuilder

private object PlatformSpecific {

  def clients[F[_]: Async: Files: Network](config: KubeConfig[F]): Resource[F, Clients[F]] =
    for {
      tlsContext <- TlsContexts.fromConfig(config).toResource
      builderRaw = EmberClientBuilder.default[F]
      builder    = tlsContext.fold(builderRaw)(builderRaw.withTLSContext)
      clients <- builder.buildWebSocket
      (http, ws) = clients
    } yield Clients(http, ws)

}
