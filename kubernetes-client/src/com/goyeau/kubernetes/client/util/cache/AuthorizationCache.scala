package com.goyeau.kubernetes.client.util
package cache

import cats.effect.Async
import cats.syntax.all.*
import fs2.io.file.Path
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser.*
import org.http4s.{AuthScheme, Credentials}
import org.http4s.Credentials.Token
import org.http4s.headers.Authorization
import org.typelevel.log4cats.Logger

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

private[util] case class JwtPayload(
    exp: Option[Long]
)

object AuthorizationCache {

  implicit private val jwtPayloadCodec: Codec[JwtPayload] = deriveCodec

  private val base64 = Base64.getDecoder

  def apply[F[_]: Logger](
      retrieve: F[Authorization],
      refreshBeforeExpiration: FiniteDuration
  )(implicit F: Async[F]): F[TokenCache[F]] =
    TokenCache[F](
      retrieve = retrieve.map { token =>
        val expirationTimestamp =
          token match {
            case Authorization(Credentials.Token(AuthScheme.Bearer, token)) =>
              token.split('.') match {
                case Array(_, payload, _) =>
                  Try(new String(base64.decode(payload), StandardCharsets.US_ASCII)).toOption
                    .flatMap(payload => decode[JwtPayload](payload).toOption)
                    .flatMap(_.exp)
                    .map(Instant.ofEpochSecond)

                case _ =>
                  none
              }
            case _ => none
          }

        CachedAuthorization(expirationTimestamp = expirationTimestamp, token = token)
      },
      refreshBeforeExpiration = refreshBeforeExpiration
    )

}
