package com.goyeau.kubernetes.client.api

import java.util.Base64

import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import io.circe._
import io.k8s.api.core.v1.{Secret, SecretList}
import org.http4s.Status
import org.http4s.client.Client
import org.http4s.Uri.uri

private[client] case class SecretsApi[F[_]](httpClient: Client[F], config: KubeConfig)(
  implicit
  val F: Sync[F],
  val listDecoder: Decoder[SecretList],
  encoder: Encoder[Secret],
  decoder: Decoder[Secret]
) extends Listable[F, SecretList] {
  val resourceUri = uri("/api") / "v1" / "secrets"

  def namespace(namespace: String) = NamespacedSecretsApi(httpClient, config, namespace)
}

private[client] case class NamespacedSecretsApi[F[_]](
  httpClient: Client[F],
  config: KubeConfig,
  namespace: String
)(
  implicit
  val F: Sync[F],
  val resourceEncoder: Encoder[Secret],
  val resourceDecoder: Decoder[Secret],
  val listDecoder: Decoder[SecretList]
) extends Creatable[F, Secret]
    with Replaceable[F, Secret]
    with Gettable[F, Secret]
    with Listable[F, SecretList]
    with Deletable[F]
    with GroupDeletable[F] {
  val resourceUri = uri("/api") / "v1" / "namespaces" / namespace / "secrets"

  def createEncode(resource: Secret): F[Status] = create(encode(resource))

  def createOrUpdateEncode(resource: Secret): F[Status] =
    createOrUpdate(encode(resource))

  private def encode(resource: Secret) =
    resource.copy(data = resource.data.map(_.mapValues(v => Base64.getEncoder.encodeToString(v.getBytes))))
}
