package com.goyeau.kubernetes.client.crd

import cats.syntax.either._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import io.k8s.apiextensionsapiserver.pkg.apis.apiextensions.v1.JSONSchemaProps

trait JSONSchemaPropsOrArray
case class SchemaNotArrayValue(value: JSONSchemaProps) extends JSONSchemaPropsOrArray
case class ArrayValue(value: Array[JSONSchemaProps])   extends JSONSchemaPropsOrArray

object JSONSchemaPropsOrArray {
  implicit val encode: Encoder[JSONSchemaPropsOrArray] = {
    case SchemaNotArrayValue(schema) => schema.asJson
    case ArrayValue(array)           => array.asJson
  }

  implicit val decode: Decoder[JSONSchemaPropsOrArray] = cursor => {
    val decodeSchema = cursor.as[JSONSchemaProps].map(SchemaNotArrayValue.apply)
    val decodeArray  = cursor.as[Array[JSONSchemaProps]].map(ArrayValue.apply)
    decodeSchema.leftFlatMap(_ => decodeArray)
  }
}
