package com.goyeau.kubernetes.client

import cats.effect.IO
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.LoggerName

object TestPlatformSpecific {
  
    def getLogger(implicit name: LoggerName): Logger[IO] = new Logger[IO] {
        def error(message: => String): IO[Unit] = IO(println(message))
        def warn(message: => String): IO[Unit] = IO(println(message))
        def info(message: => String): IO[Unit] = IO(println(message))
        def debug(message: => String): IO[Unit] = IO(println(message))
        def trace(message: => String): IO[Unit] = IO(println(message))

        def error(t: Throwable)(message: => String): IO[Unit] = IO(println(message))
        def warn(t: Throwable)(message: => String): IO[Unit] = IO(println(message))
        def info(t: Throwable)(message: => String): IO[Unit] = IO(println(message))
        def debug(t: Throwable)(message: => String): IO[Unit] = IO(println(message))
        def trace(t: Throwable)(message: => String): IO[Unit] = IO(println(message))
    }

}
