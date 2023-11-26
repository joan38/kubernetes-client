package com.goyeau.kubernetes.client.util
package cache

import cats.effect.Async
import cats.syntax.all.*
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.circe.parser.*
import org.http4s.{AuthScheme, Credentials}
import org.http4s.headers.Authorization
import scala.concurrent.duration.*

private[util] case class JwtPayload(
    exp: Option[Long]
)

object AuthorizationParse {

  implicit private val jwtPayloadCodec: Codec[JwtPayload] = deriveCodec

  def apply[F[_]](retrieve: F[Authorization])(implicit F: Async[F]): F[AuthorizationWithExpiration] =
    retrieve
      .flatMap { token =>
        (token match {
          case Authorization(Credentials.Token(AuthScheme.Bearer, token)) =>
            token.split('.') match {
              case Array(_, payload, _) =>
                fs2.Stream
                  .emit(payload)
                  .covary[F]
                  .through(fs2.text.base64.decode)
                  .through(fs2.text.utf8.decode)
                  .compile
                  .last
                  .flatMap {
                    case Some(payload) =>
                      F
                        .fromEither(decode[JwtPayload](payload))
                        .map(_.exp)
                        .flatMap {
                          case Some(expiration) => F.delay(expiration.seconds.some)
                          case None             => none[FiniteDuration].pure[F]
                        }

                    case None =>
                      none[FiniteDuration].pure[F]

                  }

              case _ =>
                none[FiniteDuration].pure[F]
            }
          case _ => none[FiniteDuration].pure[F]
        }).map { expirationTimestamp =>
          AuthorizationWithExpiration(expirationTimestamp = expirationTimestamp, authorization = token)
        }
      }

}
