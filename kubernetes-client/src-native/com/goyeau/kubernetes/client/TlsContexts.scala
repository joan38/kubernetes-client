package com.goyeau.kubernetes.client

import cats.syntax.all.*
import cats.effect.syntax.all.*
import cats.effect.*
import cats.effect.std.Env
import scodec.bits.ByteVector
import fs2.io.net.tls.{CertChainAndKey, S2nConfig, TLSContext}
import fs2.io.net.Network
import fs2.io.file.{Files, Path}

private[client] object TlsContexts {

  def fromConfig[F[_]: Sync: Network: Files: Env](config: KubeConfig[F]): Resource[F, Option[TLSContext[F]]] =
    mkSecureContext(config).map(_.map(Network[F].tlsContext.fromS2nConfig(_)))

  // https://github.com/aws/s2n-tls/blob/main/docs/USAGE-GUIDE.md#security-policies
  private val ENV_TLS_CIPHER_PREFERENCES = "TLS_CIPHER_PREFERENCES"

  private val ENV_TLS_WIPED_TRUST_STORE         = "TLS_WIPED_TRUST_STORE"
  private val ENV_TLS_SEND_BUFFER_SIZE          = "TLS_SEND_BUFFER_SIZE"
  private val ENV_TLS_DISABLE_X509_VERIFICATION = "TLS_DISABLE_X509_VERIFICATION"
  private val ENV_TLS_MAX_CERT_CHAIN_DEPTH      = "TLS_MAX_CERT_CHAIN_DEPTH"
  private val ENV_TLS_DH_PARAMS                 = "TLS_DH_PARAMS"

  private def mkSecureContext[F[_]: Sync: Files: Env](config: KubeConfig[F]): Resource[F, Option[S2nConfig]] =
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
      cipherPreferences <- Env[F].get(ENV_TLS_CIPHER_PREFERENCES).toResource
      wipedTrustStore   <- Env[F].get(ENV_TLS_WIPED_TRUST_STORE).map(_.filter(_.toLowerCase == "yes")).toResource
      sendBufferSize <- Env[F]
        .get(ENV_TLS_SEND_BUFFER_SIZE)
        .flatMap(s => Sync[F].delay(s.map(_.toInt)))
        .adaptError { error =>
          new IllegalArgumentException(
            s"failed to parse the value specified in $ENV_TLS_SEND_BUFFER_SIZE (32 bit): ${error.getMessage}"
          )
        }
        .toResource
      disabledX509Verification <- Env[F]
        .get(ENV_TLS_DISABLE_X509_VERIFICATION)
        .map(_.filter(_.toLowerCase == "yes"))
        .toResource
      maxCertChainDepth <- Env[F]
        .get(ENV_TLS_MAX_CERT_CHAIN_DEPTH)
        .flatMap(s => Sync[F].delay(s.map(_.toShort)))
        .adaptError { error =>
          new IllegalArgumentException(
            s"failed to parse the value specified in $ENV_TLS_MAX_CERT_CHAIN_DEPTH (16 bit): ${error.getMessage}"
          )
        }
        .toResource
      dhParams <- Env[F].get(ENV_TLS_DH_PARAMS).toResource
      _ <- Sync[F]
        .raiseWhen(config.clientKeyPass.nonEmpty)(new IllegalArgumentException("client key password is not supported"))
        .toResource
      builder =
        if (keyBytes.nonEmpty || certBytes.nonEmpty || caBytes.nonEmpty) {
          // scalafix:off DisableSyntax.var
          var builder =
            S2nConfig.builder
              .withMaxCertChainDepth(5)
          // scalafix:on DisableSyntax.var

          builder = cipherPreferences.fold(builder)(builder.withCipherPreferences)
          builder = wipedTrustStore.fold(builder)(_ => builder.withWipedTrustStore)
          builder = sendBufferSize.fold(builder)(builder.withSendBufferSize)
          builder = disabledX509Verification
            .fold(builder)(_ => builder.withDisabledX509Verification)
          builder = maxCertChainDepth.fold(builder)(builder.withMaxCertChainDepth)
          builder = dhParams.fold(builder)(builder.withDHParams)

          builder = (certBytes, keyBytes).tupled.fold(builder) { case (certBytes, keyBytes) =>
            println(
              s"setting cert and keys:\n${certBytes.decodeAscii.toOption.get},\n${keyBytes.decodeAscii.toOption.get}"
            )
            builder.withCertChainAndKeysToStore(
              List(
                CertChainAndKey(
                  chainPem = certBytes,
                  privateKeyPem = keyBytes
                )
              )
            )
          }

          builder = caBytes.fold(builder) { caBytes =>
            println(s"setting ca:\n$caBytes")
            builder.withPemsToTrustStore(
              List(
                caBytes
              )
            )
          }

          builder.some
        } else
          none
      result <- builder match {
        case Some(builder) => builder.build[F].map(_.some)
        case None          => none.pure[F].toResource
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
