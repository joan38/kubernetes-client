package com.goyeau.kubernetes.client

import cats.implicits._
import cats.effect.{Sync, Timer}
import scala.concurrent.duration._

object Utils {
  def retry[F[_]: Sync, Result](f: F[Result], initialDelay: FiniteDuration = 500.millis, maxRetries: Int = 50)(
      implicit timer: Timer[F]
  ): F[Result] =
    f.handleErrorWith { exception =>
      if (maxRetries > 0) timer.sleep(initialDelay) *> retry(f, initialDelay * 2, maxRetries - 1)
      else Sync[F].raiseError(exception)
    }
}
