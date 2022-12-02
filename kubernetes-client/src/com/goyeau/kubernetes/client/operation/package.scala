package com.goyeau.kubernetes.client

import cats.syntax.all.*
import cats.{Applicative, FlatMap}
import cats.effect.Resource
import com.goyeau.kubernetes.client.util.CachedExecToken
import org.http4s.Credentials.Token
import org.http4s.client.Client
import org.http4s.{AuthScheme, EntityDecoder, Request, Response}
import org.http4s.headers.Authorization

package object operation {
  implicit private[client] class KubernetesRequestOps[F[_]: Applicative](request: Request[F]) {
    def withOptionalAuthorization(
        auth: Option[F[Authorization]],
        cachedExecToken: Option[CachedExecToken[F]]
    ): F[Request[F]] =
      cachedExecToken match {
        case None => auth.fold(request.pure[F])(auth => auth.map(request.putHeaders(_)))
        case Some(cachedExecToken) =>
          cachedExecToken.get.map(token =>
            request.putHeaders(
              Authorization(Token(AuthScheme.Bearer, token))
            )
          )
      }

  }
  implicit private[client] class HttpClientOps[F[_]: FlatMap](httpClient: Client[F]) {

    def runF(
        request: F[Request[F]]
    ): Resource[F, Response[F]] =
      Resource.eval(request).flatMap(httpClient.run)

    def expectOptionF[A](req: F[Request[F]])(implicit d: EntityDecoder[F, A]): F[Option[A]] =
      req.flatMap(httpClient.expectOption[A])

    def expectF[A](req: F[Request[F]])(implicit d: EntityDecoder[F, A]): F[A] =
      req.flatMap(httpClient.expect[A])

  }

}
