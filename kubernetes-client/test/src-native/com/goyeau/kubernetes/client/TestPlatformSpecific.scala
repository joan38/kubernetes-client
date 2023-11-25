package com.goyeau.kubernetes.client

import cats.effect.IO
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.console.ConsoleLogger
import org.typelevel.log4cats.LoggerName

object TestPlatformSpecific {
  
    def getLogger(implicit name: LoggerName): Logger[IO] = new ConsoleLogger[IO]()

}
