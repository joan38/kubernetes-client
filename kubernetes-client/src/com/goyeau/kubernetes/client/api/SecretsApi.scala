package com.goyeau.kubernetes.client.api

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import com.goyeau.kubernetes.client.util.CachedExecToken
import io.circe._
import io.k8s.api.core.v1.{Secret, SecretList}
import org.http4s.client.Client
import org.http4s.implicits._
import org.http4s.{Status, Uri}

import java.util.Base64

private[client] class SecretsApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val cachedExecToken: Option[CachedExecToken[F]]
)(implicit
    val F: Async[F],
    val listDecoder: Decoder[SecretList],
    val resourceDecoder: Decoder[Secret],
    encoder: Encoder[Secret]
) extends Listable[F, SecretList]
    with Watchable[F, Secret] {
  val resourceUri = uri"/api" / "v1" / "secrets"

  def namespace(namespace: String) = new NamespacedSecretsApi(httpClient, config, cachedExecToken, namespace)
}

private[client] class NamespacedSecretsApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val cachedExecToken: Option[CachedExecToken[F]],
    namespace: String
)(implicit
    val F: Async[F],
    val resourceEncoder: Encoder[Secret],
    val resourceDecoder: Decoder[Secret],
    val listDecoder: Decoder[SecretList]
) extends Creatable[F, Secret]
    with Replaceable[F, Secret]
    with Gettable[F, Secret]
    with Listable[F, SecretList]
    with Deletable[F]
    with GroupDeletable[F]
    with Watchable[F, Secret] {
  val resourceUri: Uri = uri"/api" / "v1" / "namespaces" / namespace / "secrets"

  def createEncode(resource: Secret): F[Status] = create(encode(resource))

  def createOrUpdateEncode(resource: Secret): F[Status] =
    createOrUpdate(encode(resource))

  private def encode(resource: Secret) =
    resource.copy(data = resource.data.map(_.map { case (k, v) =>
      k -> Base64.getEncoder.encodeToString(v.getBytes)
    }))
}
