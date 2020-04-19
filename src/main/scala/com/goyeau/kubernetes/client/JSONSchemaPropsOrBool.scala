package com.goyeau.kubernetes.client

import cats.syntax.either._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}
import io.k8s.apiextensionsapiserver.pkg.apis.apiextensions.v1.JSONSchemaProps

trait JSONSchemaPropsOrBool
case class SchemaNotBoolValue(value: JSONSchemaProps) extends JSONSchemaPropsOrBool
case class BoolValue(value: Boolean)                  extends JSONSchemaPropsOrBool

object JSONSchemaPropsOrBool {
  implicit val encode: Encoder[JSONSchemaPropsOrBool] = {
    case SchemaNotBoolValue(schema) => schema.asJson
    case BoolValue(bool)            => Json.fromBoolean(bool)
  }

  implicit val decode: Decoder[JSONSchemaPropsOrBool] = cursor => {
    val decodeSchema = cursor.as[JSONSchemaProps].map(SchemaNotBoolValue)
    val decodeBool   = cursor.as[Boolean].map(BoolValue)
    decodeSchema.leftFlatMap(_ => decodeBool)
  }
}
