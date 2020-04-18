package com.goyeau.kubernetes.client.api

import cats.effect.{ContextShift, IO, Timer}

import scala.concurrent.ExecutionContext

trait ContextProvider {

  implicit lazy val timer: Timer[IO]               = IO.timer(ExecutionContext.global)
  implicit lazy val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

}
