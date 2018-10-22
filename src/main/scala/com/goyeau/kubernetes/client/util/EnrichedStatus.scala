package com.goyeau.kubernetes.client.util

import cats.implicits._
import cats.effect.Sync
import io.k8s.apimachinery.pkg.apis.meta.v1
import org.http4s._
import org.http4s.circe.CirceEntityCodec._

private[client] object EnrichedStatus {

  def apply[F[_]: Sync](response: Response[F]): F[Status] =
    if (response.status.isSuccess) Sync[F].delay(response.status)
    else
      for {
        reasonDecoded <- EntityDecoder[F, v1.Status].decode(response, strict = false).value
        reason <- Sync[F].fromEither(reasonDecoded)
      } yield
        response.status.withReason(s"${response.status.reason}${reason.message.map(message => s": $message").mkString}")
}
