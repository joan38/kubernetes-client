package com.goyeau.kubernetes.client.util

import cats.effect.Sync
import io.circe.{Decoder, Encoder, Printer}
import org.http4s.{EntityDecoder, EntityEncoder}
import org.http4s.circe.CirceInstances

private[client] object CirceEntityCodec extends CirceInstances {
  override val defaultPrinter: Printer = Printer.noSpaces.copy(dropNullValues = true)

  implicit def circeEntityEncoder[F[_], A: Encoder]: EntityEncoder[F, A]       = jsonEncoderOf[F, A]
  implicit def circeEntityDecoder[F[_]: Sync, A: Decoder]: EntityDecoder[F, A] = jsonOf[F, A]
}
