package com.goyeau.kubernetes.client

import cats.effect.*
import cats.syntax.all.*
import org.typelevel.log4cats.Logger
import scala.concurrent.duration.*

object Utils {
  def retry[Result](
      f: IO[Result],
      initialDelay: FiniteDuration = 1.second,
      maxRetries: Int = 10,
      actionClue: Option[String] = None,
      firstRun: Boolean = true
  )(implicit log: Logger[IO]): IO[Result] =
    f
      .flatTap { _ =>
        IO.whenA(!firstRun)(log.info(s"Succeeded after retrying${actionClue.map(c => s", action: $c").getOrElse("")}"))
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
            IO.sleep(initialDelay) *>
            IO.defer(retry(f, initialDelay, maxRetries - 1, actionClue, firstRun = false))
        else
          log.info(
            s"Giving up ${actionClue.map(c => s", action: $c").getOrElse("")}. No retries left"
          ) *>
            IO.raiseError(exception)
      }
}
