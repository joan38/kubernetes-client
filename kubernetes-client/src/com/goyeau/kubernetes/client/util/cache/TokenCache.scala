package com.goyeau.kubernetes.client.util
package cache

import cats.effect.Async
import cats.syntax.all.*
import org.http4s.headers.Authorization
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.*

private[client] trait TokenCache[F[_]] {

  def get: F[Authorization]

}

object TokenCache {

  def apply[F[_]: Logger](
      retrieve: F[CachedAuthorization],
      refreshBeforeExpiration: FiniteDuration = 0.seconds
  )(implicit F: Async[F]): F[TokenCache[F]] =
    F.ref(Option.empty[CachedAuthorization]).map { cache =>
      new TokenCache[F] {
        override def get: F[Authorization] = {

          def getAndCacheToken: F[CachedAuthorization] =
            retrieve.flatMap { token =>
              cache.set(token.some).as(token)
            }

          cache.get
            .flatMap {
              case Some(cached) =>
                F.realTimeInstant
                  .flatMap { now =>
                    if (
                      cached.expirationTimestamp.exists(_.isBefore(now.minusSeconds(refreshBeforeExpiration.toSeconds)))
                    ) {
                      getAndCacheToken
                    } else {
                      cached.pure[F]
                    }
                  }
              case None => getAndCacheToken
            }
            .map(_.token)

        }

      }
    }

}
