package com.goyeau.kubernetes.client

import cats.effect.*
import org.http4s.jdkhttpclient.{JdkHttpClient, JdkWSClient}

import java.net.http.HttpClient

private object PlatformSpecific {
  def clients[F[_]: Async](config: KubeConfig[F]): Resource[F, Clients[F]] = for {
    client <- Resource.eval {
      Sync[F].delay(HttpClient.newBuilder().sslContext(util.SslContexts.fromConfig(config)).build())
    }
    httpClient <- Resource.pure(JdkHttpClient[F](client))
    wsClient   <- Resource.pure(JdkWSClient[F](client))
  } yield Clients(httpClient, wsClient)
}
