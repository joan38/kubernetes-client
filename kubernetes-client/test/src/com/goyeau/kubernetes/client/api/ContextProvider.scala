package com.goyeau.kubernetes.client.api

import cats.Parallel
import cats.effect.unsafe.implicits.global
import cats.effect.IO

trait ContextProvider {
  def unsafeRunSync[A](f: IO[A]): A = f.unsafeRunSync()

  implicit lazy val parallel: Parallel[IO] = IO.parallelForIO
}
