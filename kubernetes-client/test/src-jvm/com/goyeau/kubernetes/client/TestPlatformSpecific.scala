package com.goyeau.kubernetes.client

import cats.effect.*
import cats.effect.std.Env
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.LoggerName

object TestPlatformSpecific {

  def mkClient(implicit L: Logger[IO]): Resource[IO, KubernetesClient[IO]] =
    Env[IO].get("KUBE_CONTEXT_NAME").toResource.flatMap { contextOverride =>
      Env[IO].get("KUBE_CLIENT_IMPLEMENTATION").toResource.flatMap { implementationOverride =>
        val kubeConfig = KubeConfig.inHomeDir[IO](
          contextOverride.getOrElse("minikube")
        )
        implementationOverride.getOrElse("jdk") match {
          case "jdk"   => KubernetesClient.jdk(kubeConfig)
          case "ember" => KubernetesClient.ember(kubeConfig)
          case other =>
            IO.raiseError(
              new IllegalArgumentException(s"unknown implementation: $other, specified in KUBE_CLIENT_IMPLEMENTATION")
            ).toResource
        }

      }
    }

  def getLogger(implicit name: LoggerName): Logger[IO] = Slf4jLogger.getLogger[IO]

}
