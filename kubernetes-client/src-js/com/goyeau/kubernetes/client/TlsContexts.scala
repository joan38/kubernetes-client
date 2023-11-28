package com.goyeau.kubernetes.client

import cats.effect.syntax.all.*
import cats.syntax.all.*
import cats.effect.*
import cats.data.OptionT
import cats.effect.std.Env
import fs2.io.net.tls.{SecureContext, TLSContext}
import fs2.io.net.Network
import fs2.io.file.{Files, Path}
import fs2.Chunk
import fs2.io.net.tls.SecureContext.SecureVersion

import scala.concurrent.duration.*

private[client] object TlsContexts {

  def fromConfig[F[_]: Sync: Network: Files: Env](config: KubeConfig[F]): Resource[F, Option[TLSContext[F]]] =
    OptionT(mkSecureContext(config)).map(Network[F].tlsContext.fromSecureContext(_)).value.toResource

  // https://nodejs.org/api/tls.html#tls_tls_createsecurecontext_options
  private val ENV_TLS_CIPHER_PREFERENCES     = "TLS_CIPHER_PREFERENCES"
  private val ENV_TLS_MIN_VERSION            = "TLS_MIN_VERSION"
  private val ENV_TLS_MAX_VERSION            = "TLS_MAX_VERSION"
  private val ENV_TLS_CLIENT_CERT_ENGINE     = "TLS_CLIENT_CERT_ENGINE"
  private val ENV_TLS_DH_PARAMS              = "TLS_DH_PARAMS"
  private val ENV_TLS_ECDH_CURVE             = "TLS_ECDH_CURVE"
  private val ENV_TLS_PRIVATE_KEY_ENGINE     = "TLS_PRIVATE_KEY_ENGINE"
  private val ENV_TLS_TLS_HONOR_CIPHER_ORDER = "TLS_HONOR_CIPHER_ORDER"
  private val ENV_TLS_PRIVATE_KEY_IDENTIFIER = "TLS_PRIVATE_KEY_IDENTIFIER"
  private val ENV_TLS_SECURE_OPTIONS         = "TLS_SECURE_OPTIONS"
  private val ENV_TLS_SESSION_ID_CONTEXT     = "TLS_SESSION_ID_CONTEXT"
  private val ENV_TLS_SESSION_TIMEOUT        = "TLS_SESSION_TIMEOUT"
  private val ENV_TLS_SIGALGS                = "TLS_SIGALGS"

  private def mkSecureContext[F[_]: Sync: Files: Env](config: KubeConfig[F]): F[Option[SecureContext]] =
    for {
      // ca
      caDataBytes <- decodeBase64[F](config.caCertData)
      caFileBytes <- readFile(config.caCertFile)
      // Client certificate
      certDataBytes <- decodeBase64(config.clientCertData)
      certFileBytes <- readFile(config.clientCertFile)
      // Client key
      keyDataBytes <- decodeBase64(config.clientKeyData)
      keyFileBytes <- readFile(config.clientKeyFile)
      // ---
      keyBytes  = keyDataBytes.orElse(keyFileBytes)
      certBytes = certDataBytes.orElse(certFileBytes)
      caBytes   = caDataBytes.orElse(caFileBytes)
      cipherPreferences    <- Env[F].get(ENV_TLS_CIPHER_PREFERENCES)
      minVersion           <- Env[F].get(ENV_TLS_MIN_VERSION).flatMap(parseSecureVersion(_, ENV_TLS_MIN_VERSION))
      maxVersion           <- Env[F].get(ENV_TLS_MAX_VERSION).flatMap(parseSecureVersion(_, ENV_TLS_MAX_VERSION))
      clientCertEngine     <- Env[F].get(ENV_TLS_CLIENT_CERT_ENGINE)
      dhParam              <- Env[F].get(ENV_TLS_DH_PARAMS)
      ecdhCurve            <- Env[F].get(ENV_TLS_ECDH_CURVE)
      honorCipherOrder     <- Env[F].get(ENV_TLS_TLS_HONOR_CIPHER_ORDER).map(_.map(_.toLowerCase == "yes"))
      privateKeyEngine     <- Env[F].get(ENV_TLS_PRIVATE_KEY_ENGINE)
      privateKeyIdentifier <- Env[F].get(ENV_TLS_PRIVATE_KEY_IDENTIFIER)
      secureOptions <- Env[F]
        .get(ENV_TLS_SECURE_OPTIONS)
        .flatMap(s => Sync[F].delay(s.map(_.toLong)))
        .adaptError { error =>
          new IllegalArgumentException(
            s"failed to parse the value specified in $ENV_TLS_SECURE_OPTIONS (64 bit): ${error.getMessage}"
          )
        }
      sessionIdContext <- Env[F].get(ENV_TLS_SESSION_ID_CONTEXT)
      sessionTimeout <- Env[F]
        .get(ENV_TLS_SESSION_TIMEOUT)
        .flatMap(t => Sync[F].delay(t.map(_.toLong.seconds)))
        .adaptError { error =>
          new IllegalArgumentException(
            s"failed to parse the value specified in $ENV_TLS_SESSION_TIMEOUT (seconds): ${error.getMessage}"
          )
        }
      sigalgs <- Env[F].get(ENV_TLS_SIGALGS)
    } yield
      if (keyBytes.nonEmpty || certBytes.nonEmpty || caBytes.nonEmpty)
        SecureContext(
          ca = caBytes.map(caBytes => Seq(caBytes.asLeft[String])),
          cert = certBytes.map(certBytes => Seq(certBytes.asLeft[String])),
          ciphers = cipherPreferences,
          clientCertEngine = clientCertEngine,
          // crl: Option[Seq[Either[Chunk[Byte], String]]] = None,
          dhparam = dhParam.map(_.asRight),
          ecdhCurve = ecdhCurve,
          honorCipherOrder = honorCipherOrder,
          key = keyBytes.map { keyBytes =>
            Seq(SecureContext.Key(keyBytes.asLeft, config.clientKeyPass))
          },
          maxVersion = maxVersion,
          minVersion = minVersion,
          passphrase = config.clientKeyPass,
          // pfx: Option[Seq[Pfx]] = None,
          privateKeyEngine = privateKeyEngine,
          privateKeyIdentifier = privateKeyIdentifier,
          secureOptions = secureOptions,
          sessionIdContext = sessionIdContext,
          sessionTimeout = sessionTimeout,
          sigalgs = sigalgs
          // ticketKeys: Option[Chunk[Byte]] = None
        ).some
      else
        none

  private val secureVersions: Map[String, SecureVersion] = Map(
    "TLSv1"   -> SecureVersion.TLSv1,
    "TLSv1.1" -> SecureVersion.`TLSv1.1`,
    "TLSv1.2" -> SecureVersion.`TLSv1.2`,
    "TLSv1.3" -> SecureVersion.`TLSv1.3`
  )

  private def parseSecureVersion[F[_]: Sync](s: Option[String], envVar: String): F[Option[SecureVersion]] =
    s.traverse { s =>
      Sync[F].fromOption(
        secureVersions.get(s),
        new IllegalArgumentException(
          s"invalid TLS version specified in $envVar: $s, valid values: ${secureVersions.keys.mkString(", ")}"
        )
      )
    }

  private def decodeBase64[F[_]: Sync](data: Option[String]): F[Option[Chunk[Byte]]] =
    data.traverse { data =>
      fs2.Stream
        .emit(data)
        .covary[F]
        .through(fs2.text.base64.decode)
        .chunkAll
        .compile
        .lastOrError
    }

  private def readFile[F[_]: Sync: Files](path: Option[Path]): F[Option[Chunk[Byte]]] =
    path.traverse(path => Files[F].readAll(path).chunkAll.compile.lastOrError)

}
