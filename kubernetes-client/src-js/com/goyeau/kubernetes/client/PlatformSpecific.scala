package com.goyeau.kubernetes.client

import cats.effect.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.client.EmberClientBuilder

private object PlatformSpecific {

  def clients[F[_]: Async](config: KubeConfig[F]): Resource[F, Clients[F]] = {
    for {
      clients <- EmberClientBuilder.default[F].buildWebSocket
      (http, ws) = clients
    } yield Clients(http, ws)
    
  }

}
