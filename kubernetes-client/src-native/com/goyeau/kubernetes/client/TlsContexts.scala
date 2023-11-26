package com.goyeau.kubernetes.client

import cats.syntax.all.*
import cats.effect.syntax.all.*
import cats.effect.*
import com.goyeau.kubernetes.client.KubeConfig
import scodec.bits.ByteVector
import fs2.io.net.tls.{S2nConfig, TLSContext, CertChainAndKey}
import fs2.io.net.Network
import fs2.io.file.{Files, Path}

private[client] object TlsContexts {

  def fromConfig[F[_]: Sync: Network: Files](config: KubeConfig[F]): Resource[F, Option[TLSContext[F]]] =
    mkSecureContext(config).map(_.map(Network[F].tlsContext.fromS2nConfig(_)))

  private def mkSecureContext[F[_]: Sync: Files](config: KubeConfig[F]): Resource[F, Option[S2nConfig]] =
    for {
      // ca
      caDataBytes <- decodeBase64String(config.caCertData).toResource
      caFileBytes <- readFileString(config.caCertFile).toResource
      // Client certificate
      certDataBytes <- decodeBase64ByteVector(config.clientCertData).toResource
      certFileBytes <- readFileByteVector(config.clientCertFile).toResource
      // Client key
      keyDataBytes <- decodeBase64ByteVector(config.clientKeyData).toResource
      keyFileBytes <- readFileByteVector(config.clientKeyFile).toResource
      // ---
      keyBytes  = keyDataBytes.orElse(keyFileBytes)
      certBytes = certDataBytes.orElse(certFileBytes)
      caBytes   = caDataBytes.orElse(caFileBytes)
      _ <- Sync[F].raiseWhen(config.clientKeyPass.nonEmpty)(new IllegalArgumentException("do we support client key password?")).toResource
      builder = if (keyBytes.nonEmpty || certBytes.nonEmpty || caBytes.nonEmpty) {
                  var builder = S2nConfig.builder.withCipherPreferences("default_tls13")

                  builder = (certBytes, keyBytes).tupled.fold(builder) {
                    case (certBytes, keyBytes) => 
                      println(s"setting cert and keys: $certBytes, $keyBytes")
                      builder.withCertChainAndKeysToStore(
                        List(
                          CertChainAndKey(
                            chainPem = certBytes,
                            privateKeyPem = keyBytes,
                          )
                        )
                      )
                  }         
                  builder = caBytes.fold(builder) { caBytes =>
                    println(s"setting ca: $caBytes")
                    builder.withPemsToTrustStore(
                      List(
                        caBytes
                      )
                    )
                  }
                    //   def withWipedTrustStore: Builder
                    //   def withSendBufferSize(size: Int): Builder
                    //   def withVerifyHostCallback(cb: String => SyncIO[Boolean]): Builder
                    //   def withDisabledX509Verification: Builder
                    //   def withMaxCertChainDepth(maxDepth: Short): Builder
                    //   def withDHParams(dhparams: String): Builder
                    //   def withCipherPreferences(version: String): Builder
                  builder.some
                } else
                  none
      _ = println(s"builder: $builder")
      result <- builder match {
                  case Some(builder) => builder.build[F].map(_.some)
                  case None => none.pure[F].toResource
                }
    } yield result
      

  private def decodeString[F[_]: Sync](s: fs2.Stream[F, Byte]): F[String] = 
    s.through(fs2.text.utf8.decode).compile.string

  private def decodeByteVector[F[_]: Sync](s: fs2.Stream[F, Byte]): F[ByteVector] = 
    s.compile.to(ByteVector)

  private def decodeBase64[F[_]: Sync](data: String): fs2.Stream[F, Byte] =
    fs2.Stream
      .emit(data)
      .covary[F]
      .through(fs2.text.base64.decode)

  private def decodeBase64String[F[_]: Sync](data: Option[String]): F[Option[String]] =
    data.traverse { data =>
      decodeString(
        decodeBase64(data)
      )      
    }

  private def decodeBase64ByteVector[F[_]: Sync](data: Option[String]): F[Option[ByteVector]] =
    data.traverse { data =>
      decodeByteVector(
        decodeBase64(data)
      )      
    }

  private def readFileString[F[_]: Sync: Files](path: Option[Path]): F[Option[String]] =
    path.traverse(path => decodeString(Files[F].readAll(path)))

  private def readFileByteVector[F[_]: Sync: Files](path: Option[Path]): F[Option[ByteVector]] =
    path.traverse(path => decodeByteVector(Files[F].readAll(path)))


}
