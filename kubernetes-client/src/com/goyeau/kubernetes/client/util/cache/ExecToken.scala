package com.goyeau.kubernetes.client.util
package cache

import cats.effect.Async
import cats.syntax.all.*
import com.goyeau.kubernetes.client.util.Yamls.*
import fs2.io.IOException
import io.circe.parser.*
import org.http4s.AuthScheme
import org.http4s.Credentials.Token
import org.http4s.headers.Authorization
import org.typelevel.log4cats.Logger

import java.time.Instant
import scala.sys.process.Process
import scala.util.control.NonFatal

private[client] object ExecToken {

  def apply[F[_]: Logger](exec: AuthInfoExec)(implicit F: Async[F]): F[AuthorizationWithExpiration] =
    F
      .blocking {
        val env = exec.env.getOrElse(Seq.empty).map(e => e.name -> e.value)
        val cmd = Seq.concat(
          Seq(exec.command),
          exec.args.getOrElse(Seq.empty)
        )
        Process(cmd, None, env*).!!
      }
      .onError { case e: IOException =>
        Logger[F].error(
          s"Failed to execute the credentials plugin: ${exec.command}: ${e.getMessage}.${exec.installHint
              .fold("")(hint => s"\n$hint")}"
        )
      }
      .flatMap { output =>
        F.fromEither(
          decode[ExecCredential](output)
        )
      }
      .flatMap { execCredential =>
        execCredential.status.token match {
          case Some(token) =>
            F
              .delay(Instant.parse(execCredential.status.expirationTimestamp))
              .adaptError { case NonFatal(error) =>
                new IllegalArgumentException(
                  s"Failed to parse `.status.expirationTimestamp`: ${execCredential.status.expirationTimestamp}: ${error.getMessage}",
                  error
                )
              }
              .map { expirationTimestamp =>
                AuthorizationWithExpiration(
                  expirationTimestamp = expirationTimestamp.some,
                  authorization = Authorization(Token(AuthScheme.Bearer, token))
                )
              }
          case None =>
            F.raiseError(
              new UnsupportedOperationException(
                "Missing `.status.token` in the credentials plugin output: client certificate/client key is not supported, token is required"
              )
            )
        }
      }

}
