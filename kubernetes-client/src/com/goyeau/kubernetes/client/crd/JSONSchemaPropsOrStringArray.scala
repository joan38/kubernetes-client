package com.goyeau.kubernetes.client.crd

import cats.syntax.either.*
import io.circe.syntax.*
import io.circe.{Decoder, Encoder}
import io.k8s.apiextensionsapiserver.pkg.apis.apiextensions.v1.JSONSchemaProps

trait JSONSchemaPropsOrStringArray
case class SchemaNotStringArrayValue(value: JSONSchemaProps) extends JSONSchemaPropsOrStringArray
case class StringArrayValue(value: Array[String])            extends JSONSchemaPropsOrStringArray

object JSONSchemaPropsOrStringArray {
  implicit val encode: Encoder[JSONSchemaPropsOrStringArray] = {
    case SchemaNotStringArrayValue(schema) => schema.asJson
    case StringArrayValue(array)           => array.asJson
  }

  implicit val decode: Decoder[JSONSchemaPropsOrStringArray] = cursor => {
    val decodeSchema = cursor.as[JSONSchemaProps].map(SchemaNotStringArrayValue.apply)
    val decodeArray  = cursor.as[Array[String]].map(StringArrayValue.apply)
    decodeSchema.leftFlatMap(_ => decodeArray)
  }
}
