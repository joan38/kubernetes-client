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
import fs2.io.process.Processes
import fs2.io.process.ProcessBuilder

import java.time.Instant

private[client] object ExecToken {
  
  def apply[F[_]: Logger: Processes](exec: AuthInfoExec)(implicit F: Async[F]): F[AuthorizationWithExpiration] = {
      val env = exec.env.getOrElse(Seq.empty).view.map(e => e.name -> e.value).toMap

      val processBuilder = ProcessBuilder(
        command = exec.command, 
        args = exec.args.getOrElse(List.empty), 
      ).withExtraEnv(env)
      
      processBuilder.spawn[F].use { p =>
        p.stdout
          .through(fs2.text.utf8.decode).compile.string
          .flatMap { output => F.fromEither(decode[ExecCredential](output)) }
          .flatMap { execCredential =>
            execCredential.status.token match {
              case Some(token) =>
                F
                  .delay(Instant.parse(execCredential.status.expirationTimestamp))
                  .adaptError { error =>
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
                F.raiseError[AuthorizationWithExpiration](
                  new UnsupportedOperationException(
                    "Missing `.status.token` in the credentials plugin output: client certificate/client key is not supported, token is required"
                  )
                )
            }
          }
      }
      .onError { case e: IOException =>
        Logger[F].error(
          s"Failed to execute the credentials plugin: ${exec.command}${exec.args.fold("")(_.mkString(" ", " ", ""))}: ${e.getMessage}.${exec.installHint
              .fold("")(hint => s"\n$hint")}"
        )
      }
      
    }
      

}
