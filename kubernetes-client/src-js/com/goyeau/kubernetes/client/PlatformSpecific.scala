package com.goyeau.kubernetes.client

import cats.effect.*

private object PlatformSpecific {
  def clients[F[_]: Async](config: KubeConfig[F]): Resource[F, Clients[F]] = ???
}
