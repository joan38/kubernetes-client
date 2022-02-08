package com.goyeau.kubernetes.client

import cats.effect.Temporal
import cats.implicits.*
import cats.{ApplicativeError, Defer}

import scala.concurrent.duration.*

object Utils {
  def retry[F[_], Result](f: F[Result], initialDelay: FiniteDuration = 500.millis, maxRetries: Int = 50)(implicit
      temporal: Temporal[F],
      F: ApplicativeError[F, Throwable],
      D: Defer[F]
  ): F[Result] =
    f.handleErrorWith { exception =>
      if (maxRetries > 0) temporal.sleep(initialDelay) *> D.defer(retry(f, initialDelay * 2, maxRetries - 1))
      else F.raiseError(exception)
    }
}
