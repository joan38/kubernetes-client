package com.goyeau.kubernetes.client

import org.http4s.Request
import org.http4s.headers.Authorization

package object operation {
  implicit private[client] class KubernetesRequestOps[F[_]](request: Request[F]) {
    def withOptionalAuthorization(auth: Option[Authorization]): Request[F] =
      auth.fold(request)(request.putHeaders(_))
  }
}
