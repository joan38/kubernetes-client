package com.goyeau.kubernetes.client.util

import cats.implicits._
import cats.effect.Async
import io.k8s.apimachinery.pkg.apis.meta.v1
import org.http4s._
import org.http4s.circe.CirceEntityCodec._

private[client] object EnrichedStatus {
  def apply[F[_]](response: Response[F])(implicit F: Async[F]): F[Status] =
    if (response.status.isSuccess) F.delay(response.status)
    else
      for {
        reasonDecoded <- EntityDecoder[F, v1.Status].decode(response, strict = false).value
        reason        <- F.fromEither(reasonDecoded)
      } yield response.status.withReason(
        s"${response.status.reason}${reason.message.map(message => s": $message").mkString}"
      )
}
