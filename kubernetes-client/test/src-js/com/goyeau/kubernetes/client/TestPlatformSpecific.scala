package com.goyeau.kubernetes.client

import cats.effect.IO
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.console.ConsoleLogger
import org.typelevel.log4cats.LoggerName

object TestPlatformSpecific {

  def getLogger(implicit name: LoggerName): Logger[IO] = {
    val console = new ConsoleLogger[IO]()
    new Logger[IO] {

      override def trace(t: Throwable)(message: => String): IO[Unit] =
        console.trace(t)(s"[${name.value}] $message")

      override def trace(message: => String): IO[Unit] =
        console.trace(s"[${name.value}] $message")

      override def debug(t: Throwable)(message: => String): IO[Unit] =
        console.debug(t)(s"[${name.value}] $message")

      override def debug(message: => String): IO[Unit] =
        console.debug(s"[${name.value}] $message")

      override def info(t: Throwable)(message: => String): IO[Unit] =
        console.info(t)(s"[${name.value}] $message")

      override def info(message: => String): IO[Unit] =
        console.info(s"[${name.value}] $message")

      override def warn(t: Throwable)(message: => String): IO[Unit] =
        console.warn(t)(s"[${name.value}] $message")

      override def warn(message: => String): IO[Unit] =
        console.warn(s"[${name.value}] $message")

      override def error(t: Throwable)(message: => String): IO[Unit] =
        console.error(t)(s"[${name.value}] $message")

      override def error(message: => String): IO[Unit] =
        console.error(s"[${name.value}] $message")

    }
  }

}
