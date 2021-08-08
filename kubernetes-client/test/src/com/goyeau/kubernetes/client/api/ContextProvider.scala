package com.goyeau.kubernetes.client.api

import cats.Parallel
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}

import scala.concurrent.duration.DurationInt

trait ContextProvider {

  private val dispatcher: Resource[IO, Dispatcher[IO]] = Dispatcher[IO]
  private val runTimeout                               = 1.minute

  def unsafeRunSync[A](f: IO[A]): A =
    dispatcher
      .use(d => IO.blocking(d.unsafeRunSync(f)))
      .unsafeRunTimed(runTimeout)
      .getOrElse(sys.error(s"IO execution timeout after $runTimeout"))

  implicit lazy val parallel: Parallel[IO] = IO.parallelForIO
}
