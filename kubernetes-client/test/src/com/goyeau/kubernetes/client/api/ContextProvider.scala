package com.goyeau.kubernetes.client.api

import cats.Parallel
import cats.effect.unsafe.implicits.global
import cats.effect.IO
import scala.concurrent.duration.DurationInt

trait ContextProvider {
  def unsafeRunSync[A](f: IO[A]): A = f.unsafeRunSync()

  implicit lazy val parallel: Parallel[IO] = IO.parallelForIO
}
