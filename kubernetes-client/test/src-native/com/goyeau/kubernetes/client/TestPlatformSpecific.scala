package com.goyeau.kubernetes.client

import cats.syntax.all.*
import cats.effect.*
import cats.effect.std.Env
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.LoggerName

object TestPlatformSpecific {

  def mkClient(implicit L: Logger[IO]): Resource[IO, KubernetesClient[IO]] =
    Env[IO].get("KUBE_CONTEXT_NAME").toResource.flatMap { contextOverride =>
      val kubeConfig = KubeConfig.inHomeDir[IO](
        contextOverride.getOrElse("minikube")
      )
      KubernetesClient.ember(kubeConfig)
    }

  def getLogger(implicit name: LoggerName): Logger[IO] = new Logger[IO] {
    def error(message: => String): IO[Unit] = IO(println(s"[$name] $message"))
    def warn(message: => String): IO[Unit]  = IO(println(s"[$name] $message"))
    def info(message: => String): IO[Unit]  = IO(println(s"[$name] $message"))
    def debug(message: => String): IO[Unit] = IO(println(s"[$name] $message"))
    def trace(message: => String): IO[Unit] = IO(println(s"[$name] $message"))

    def error(t: Throwable)(message: => String): IO[Unit] = IO(println(s"[$name] $message"))
    def warn(t: Throwable)(message: => String): IO[Unit]  = IO(println(s"[$name] $message"))
    def info(t: Throwable)(message: => String): IO[Unit]  = IO(println(s"[$name] $message"))
    def debug(t: Throwable)(message: => String): IO[Unit] = IO(println(s"[$name] $message"))
    def trace(t: Throwable)(message: => String): IO[Unit] = IO(println(s"[$name] $message"))
  }

}
