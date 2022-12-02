package com.goyeau.kubernetes.client

import cats.effect.Resource
import cats.syntax.all.*
import cats.{Applicative, FlatMap}
import com.goyeau.kubernetes.client.util.cache.TokenCache
import org.http4s.client.Client
import org.http4s.jdkhttpclient.WSRequest
import org.http4s.{EntityDecoder, Request, Response}

package object operation {
  implicit private[client] class KubernetesRequestOps[F[_]: Applicative](request: Request[F]) {
    def withOptionalAuthorization(
        authToken: Option[TokenCache[F]]
    ): F[Request[F]] =
      authToken.fold(request.pure[F]) { authCache =>
        authCache.get.map { auth =>
          request.putHeaders(auth)
        }
      }
  }

  implicit private[client] class KubernetesWsRequestOps[F[_]: Applicative](request: WSRequest) {
    def withOptionalAuthorization(
        authToken: Option[TokenCache[F]]
    ): F[WSRequest] =
      authToken.fold(request.pure[F]) { authCache =>
        authCache.get.map { auth =>
          request.copy(headers = request.headers.put(auth))
        }
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
