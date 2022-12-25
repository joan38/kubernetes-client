package com.goyeau.kubernetes.client

import cats.effect.Temporal
import cats.implicits.*
import cats.{ApplicativeError, Defer}
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.*

object Utils {
  def retry[F[_], Result](
      f: F[Result],
      initialDelay: FiniteDuration = 1.second,
      maxRetries: Int = 50,
      actionClue: Option[String] = None
  )(implicit
      temporal: Temporal[F],
      F: ApplicativeError[F, Throwable],
      D: Defer[F],
      log: Logger[F]
  ): F[Result] =
    f.handleErrorWith { exception =>
      if (maxRetries > 0)
        log.info(
          s"Retrying in $initialDelay${actionClue.map(c => s", action: $c").getOrElse("")}. Retries left: $maxRetries"
        ) *>
          temporal.sleep(initialDelay) *>
          D.defer(retry(f, initialDelay, maxRetries - 1, actionClue))
      else F.raiseError(exception)
    }
}
