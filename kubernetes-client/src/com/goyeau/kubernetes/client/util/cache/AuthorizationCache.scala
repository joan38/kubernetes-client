package com.goyeau.kubernetes.client.util
package cache

import cats.effect.{Clock, Concurrent}
import cats.syntax.all.*
import org.http4s.headers.Authorization
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.*

private[client] trait AuthorizationCache[F[_]] {

  def get: F[Authorization]

}

object AuthorizationCache {

  def apply[F[_]: Logger: Clock: Concurrent](
      retrieve: F[AuthorizationWithExpiration],
      refreshBeforeExpiration: FiniteDuration = 0.seconds
  ): F[AuthorizationCache[F]] =
    Concurrent[F].ref(Option.empty[AuthorizationWithExpiration]).map { cache =>
      new AuthorizationCache[F] {

        override def get: F[Authorization] = {
          val getAndCacheToken: F[Option[AuthorizationWithExpiration]] =
            retrieve.attempt
              .flatMap {
                case Right(token) =>
                  cache.set(token.some).as(token.some)
                case Left(error) =>
                  Logger[F].warn(s"failed to retrieve the authorization token: ${error.getMessage}").as(none)
              }

          cache.get
            .flatMap {
              case Some(cached) =>
                Clock[F].realTime
                  .flatMap { now =>
                    val minExpiry   = now.plus(refreshBeforeExpiration)
                    val shouldRenew = cached.expirationTimestamp.exists(_ < minExpiry)
                    if (shouldRenew)
                      getAndCacheToken.flatMap {
                        case Some(token) => token.pure[F]
                        case None =>
                          val expired = cached.expirationTimestamp.exists(_ < now)
                          Logger[F]
                            .debug(s"using the cached token (expired: $expired)") >>
                            Concurrent[F].raiseError[AuthorizationWithExpiration](
                              new IllegalStateException(
                                s"failed to retrieve a new authorization token, cached token has expired"
                              )
                            )
                      }
                    else
                      cached.pure[F]
                  }
              case None =>
                getAndCacheToken.flatMap[AuthorizationWithExpiration] {
                  case Some(token) => token.pure[F]
                  case None        => Concurrent[F].raiseError(new IllegalStateException(s"no authorization token"))
                }
            }
            .map(_.authorization)
        }

      }
    }
}
