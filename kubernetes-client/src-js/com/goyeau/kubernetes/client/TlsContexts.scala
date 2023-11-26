package com.goyeau.kubernetes.client

import cats.syntax.all.*
import cats.effect.*
import cats.data.OptionT
import com.goyeau.kubernetes.client.KubeConfig
import fs2.io.net.tls.{TLSContext, SecureContext}
import fs2.io.net.Network
import fs2.io.file.{Files, Path}
import fs2.Chunk

private[client] object TlsContexts {

  def fromConfig[F[_]: Concurrent: Network: Files](config: KubeConfig[F]): F[Option[TLSContext[F]]] = {
    OptionT(mkSecureContext(config)).map(Network[F].tlsContext.fromSecureContext(_)).value
  }

  private def mkSecureContext[F[_]: Concurrent: Files](config: KubeConfig[F]): F[Option[SecureContext]] = {
    for {
      // ca
      caDataBytes <- decodeBase64(config.caCertData)
      caFileBytes <- readFile(config.caCertFile)
      // Client certificate
      certDataBytes <- decodeBase64(config.clientCertData)
      certFileBytes <- readFile(config.clientCertFile)
      // Client key
      keyDataBytes <- decodeBase64(config.clientKeyData)
      keyFileBytes <- readFile(config.clientKeyFile)
      // ---
      keyBytes = keyDataBytes orElse keyFileBytes
      certBytes = certDataBytes orElse certFileBytes
      caBytes = caDataBytes orElse caFileBytes
    } yield if (keyBytes.nonEmpty || certBytes.nonEmpty || caBytes.nonEmpty) {
      SecureContext(
        ca = caBytes.map(caBytes => Seq(caBytes.asLeft[String])), // : Option[Seq[Either[Chunk[Byte], String]]] = None,
        cert = certBytes.map(certBytes => Seq(certBytes.asLeft[String])), // : Option[Seq[Either[Chunk[Byte], String]]] = None,
        // ciphers: Option[String] = None,
        // clientCertEngine: Option[String] = None,
        // crl: Option[Seq[Either[Chunk[Byte], String]]] = None,
        // dhparam: Option[Either[Chunk[Byte], String]] = None,
        // ecdhCurve: Option[String] = None,
        // honorCipherOrder: Option[Boolean] = None,
        key = keyBytes.map { keyBytes => 
          Seq(SecureContext.Key(keyBytes.asLeft, config.clientKeyPass))
        }, // : Option[Seq[Key]] = None,
        // maxVersion: Option[SecureVersion] = None,
        // minVersion: Option[SecureVersion] = None,
        // passphrase: Option[String] = None,
        // pfx: Option[Seq[Pfx]] = None,
        // privateKeyEngine: Option[String] = None,
        // privateKeyIdentifier: Option[String] = None,
        // secureOptions: Option[Long] = None,
        // sessionIdContext: Option[String] = None,
        // sessionTimeout: Option[FiniteDuration] = None,
        // sigalgs: Option[String] = None,
        // ticketKeys: Option[Chunk[Byte]] = None
      ).some
    } else {
      none
    }
      
      
  }

  private def decodeBase64[F[_]: Concurrent](data: Option[String]): F[Option[Chunk[Byte]]] = 
    data.traverse { data => 
      fs2.Stream.emit(data).covary[F]
        .through(fs2.text.base64.decode)
        .chunkAll
        .compile
        .lastOrError
    }
      
  private def readFile[F[_]: Concurrent: Files](path: Option[Path]): F[Option[Chunk[Byte]]] = 
    path.traverse { path => Files[F].readAll(path).chunkAll.compile.lastOrError }

}
