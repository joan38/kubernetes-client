package com.goyeau.kubernetes.client

import cats.effect.IO
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.LoggerName

object TestPlatformSpecific {
  
    def getLogger(implicit name: LoggerName): Logger[IO] = Slf4jLogger.getLogger[IO]

}
