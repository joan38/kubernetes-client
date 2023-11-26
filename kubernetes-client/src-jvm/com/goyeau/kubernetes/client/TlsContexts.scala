package com.goyeau.kubernetes.client

import cats.syntax.all.*
import cats.effect.*
import cats.data.OptionT
import com.goyeau.kubernetes.client.KubeConfig
import fs2.io.net.tls.TLSContext
import javax.net.ssl.SSLContext
import fs2.io.net.Network
import fs2.io.file.{Files, Path}
import fs2.Chunk

private[client] object TlsContexts {

  def fromConfig[F[_]: Concurrent: Network](config: KubeConfig[F]): F[Option[TLSContext[F]]] =
    OptionT(mkSecureContext(config)).map(Network[F].tlsContext.fromSSLContext(_)).value

  private def mkSecureContext[F[_]: Concurrent](config: KubeConfig[F]): F[Option[SSLContext]] =
    if (
      config.caCertData.nonEmpty ||
      config.caCertFile.nonEmpty ||
      config.clientCertData.nonEmpty ||
      config.clientCertFile.nonEmpty ||
      config.clientKeyData.nonEmpty ||
      config.clientKeyFile.nonEmpty
    )
      SslContexts.fromConfig(config).some.pure[F]
    else
      none.pure[F]

}
