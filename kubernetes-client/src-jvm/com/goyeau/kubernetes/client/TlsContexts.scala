package com.goyeau.kubernetes.client

import cats.effect.syntax.all.*
import cats.syntax.all.*
import cats.effect.*
import cats.data.OptionT
import fs2.io.net.tls.TLSContext
import javax.net.ssl.SSLContext
import fs2.io.net.Network

private[client] object TlsContexts {

  def fromConfig[F[_]: Sync: Network](config: KubeConfig[F]): Resource[F, Option[TLSContext[F]]] =
    OptionT(mkSecureContext(config)).map(Network[F].tlsContext.fromSSLContext(_)).value.toResource

  private def mkSecureContext[F[_]: Sync](config: KubeConfig[F]): F[Option[SSLContext]] =
    if (config.tlsConfigured)
      SslContexts.fromConfig(config).map(_.some)
    else
      none.pure[F]

}
