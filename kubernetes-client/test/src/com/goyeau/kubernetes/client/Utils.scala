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
      maxRetries: Int = 10,
      actionClue: Option[String] = None,
      firstRun: Boolean = true
  )(implicit
      temporal: Temporal[F],
      F: ApplicativeError[F, Throwable],
      D: Defer[F],
      log: Logger[F]
  ): F[Result] =
    f
      .flatTap { _ =>
        F.whenA(!firstRun)(log.info(s"Succeeded after retrying${actionClue.map(c => s", action: $c").getOrElse("")}"))
      }
      .handleErrorWith { exception =>
        val firstLine = exception.getMessage.takeWhile(_ != '\n')
        val message =
          if (firstLine.contains(".scala"))
            firstLine.split('/').lastOption.getOrElse(firstLine)
          else
            firstLine

        if (maxRetries > 0)
          log.info(
            s"$message. Retrying in $initialDelay${actionClue.map(c => s", action: $c").getOrElse("")}. Retries left: $maxRetries"
          ) *>
            temporal.sleep(initialDelay) *>
            D.defer(retry(f, initialDelay, maxRetries - 1, actionClue, firstRun = false))
        else
          log.info(
            s"Giving up ${actionClue.map(c => s", action: $c").getOrElse("")}. No retries left"
          ) *>
            F.raiseError(exception)
      }
}
