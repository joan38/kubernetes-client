package com.goyeau.kubernetes.client

import io.circe.{Decoder, Encoder, Json}
import cats.implicits._

trait IntOrString
case class IntValue(value: Int) extends IntOrString
case class StringValue(value: String) extends IntOrString

object IntOrString {
  implicit val encode: Encoder[IntOrString] = {
    case IntValue(int)       => Json.fromInt(int)
    case StringValue(string) => Json.fromString(string)
  }

  implicit val decode: Decoder[IntOrString] = cursor => {
    val decodeInt = cursor.as[Int].map(IntValue)
    val decodeString = cursor.as[String].map(StringValue)
    decodeInt.leftFlatMap(_ => decodeString)
  }
}
