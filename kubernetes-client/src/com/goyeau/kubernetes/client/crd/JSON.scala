package com.goyeau.kubernetes.client.crd

import io.circe.syntax.*
import io.circe.{Decoder, Encoder}

final case class JSON(value: String)

object JSON {
  implicit val encode: Encoder[JSON] = _.value.asJson
  implicit val decode: Decoder[JSON] = _.as[String].map(JSON(_))
}
