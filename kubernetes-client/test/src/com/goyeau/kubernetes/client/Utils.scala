package com.goyeau.kubernetes.client

import cats.effect.Temporal
import cats.implicits.*
import cats.{ApplicativeError, Defer}
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.*

object Utils {
  def retry[F[_], Result](f: F[Result], initialDelay: FiniteDuration = 500.millis, maxRetries: Int = 50)(
      implicit
      temporal: Temporal[F],
      F: ApplicativeError[F, Throwable],
      D: Defer[F],
      log: Logger[F]
  ): F[Result] =
    f.handleErrorWith { exception =>
      if (maxRetries > 0)
        log.info(s"Retrying in $initialDelay. Retries left: $maxRetries") *>
        temporal.sleep(initialDelay) *>
          D.defer(retry(f, initialDelay, maxRetries - 1))
      else F.raiseError(exception)
    }
}
