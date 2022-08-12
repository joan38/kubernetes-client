package com.goyeau.kubernetes.client.util

import cats.effect.Async
import cats.syntax.all._
import com.goyeau.kubernetes.client.util.Yamls._
import fs2.io.IOException
import io.circe.parser._
import org.typelevel.log4cats.Logger

import java.time.Instant
import scala.sys.process.Process

private[client] trait CachedExecToken[F[_]] {

  def get: F[String]

}

private[client] case class CachedToken(
    expirationTimestamp: Instant,
    token: String
)

private[client] object CachedExecToken {

  def apply[F[_]: Logger](exec: AuthInfoExec)(implicit F: Async[F]): F[CachedExecToken[F]] =
    F.ref(Option.empty[CachedToken]).map { cache =>
      new CachedExecToken[F] {
        override def get: F[String] = {

          def getAndCacheToken: F[String] =
            F.blocking {
              val env: Seq[(String, String)] = exec.env.fold(Seq.empty[(String, String)])(_.toSeq)
              val cmd = Seq.concat(
                Seq(exec.command),
                exec.args.getOrElse(Seq.empty)
              )
              Process(cmd, None, env: _*).!!
            }.onError { case e: IOException =>
              Logger[F].error(
                s"Failed to execute the credentials plugin: ${exec.command}: ${e.getMessage}.${exec.installHint
                    .fold("")(hint => s"\n$hint")}"
              )
            }.flatMap { output =>
              F.fromEither(
                decode[ExecCredential](output)
              )
            }.flatMap { execCredential =>
              execCredential.status.token match {
                case Some(token) =>
                  F.delay(Instant.parse(execCredential.status.expirationTimestamp))
                    .attempt
                    .flatMap {
                      case Right(expirationTimestamp) =>
                        cache
                          .set(
                            CachedToken(
                              expirationTimestamp = expirationTimestamp,
                              token = token
                            ).some
                          )
                          .as(token)
                      case Left(error) =>
                        Logger[F]
                          .error(
                            s"Failed to parse `.status.expirationTimestamp`: ${execCredential.status.expirationTimestamp}: ${error.getMessage}"
                          )
                          .as(token)
                    }
                case None =>
                  F.raiseError(
                    new UnsupportedOperationException(
                      "Missing `.status.token` in the credentials plugin output: client certificate/client key is not supported, token is required"
                    )
                  )
              }
            }

          cache.get.flatMap {
            case Some(cached) =>
              F.realTimeInstant.flatMap { now =>
                if (cached.expirationTimestamp.isBefore(now)) {
                  getAndCacheToken
                } else {
                  F.pure(cached.token)
                }
              }
            case None => getAndCacheToken
          }

        }

      }
    }

}
