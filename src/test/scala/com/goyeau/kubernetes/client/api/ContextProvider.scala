package com.goyeau.kubernetes.client.api

import cats.Parallel
import cats.effect.{ContextShift, IO, Timer}
import scala.concurrent.ExecutionContext

trait ContextProvider {
  implicit lazy val timer: Timer[IO]               = IO.timer(ExecutionContext.global)
  implicit lazy val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit lazy val parallel: Parallel[IO]         = IO.ioParallel
}
