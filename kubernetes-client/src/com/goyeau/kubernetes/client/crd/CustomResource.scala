package com.goyeau.kubernetes.client.crd

import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta

case class CustomResource[A, B](
    apiVersion: String,
    kind: String,
    metadata: Option[ObjectMeta],
    spec: A,
    status: Option[B]
)

object CustomResource {
  implicit def encoder[A: Encoder, B: Encoder]: Encoder.AsObject[CustomResource[A, B]] = deriveEncoder
  implicit def decoder[A: Decoder, B: Decoder]: Decoder[CustomResource[A, B]]          = deriveDecoder
}

case class CustomResourceList[A, B](
    items: Seq[CustomResource[A, B]],
    apiVersion: Option[String] = None,
    kind: Option[String] = None,
    metadata: Option[io.k8s.apimachinery.pkg.apis.meta.v1.ListMeta] = None
)

object CustomResourceList {
  implicit def encoder[A: Encoder, B: Encoder]: Encoder.AsObject[CustomResourceList[A, B]] =
    deriveEncoder
  implicit def decoder[A: Decoder, B: Decoder]: Decoder[CustomResourceList[A, B]] =
    deriveDecoder
}

case class CrdContext(group: String, version: String, plural: String)
