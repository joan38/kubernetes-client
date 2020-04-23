package com.goyeau.kubernetes.client.api

import java.util.Base64

import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import io.circe._
import io.k8s.api.core.v1.{Secret, SecretList}
import org.http4s.Status
import org.http4s.client.Client
import org.http4s.implicits._
import scala.collection.compat._

private[client] case class SecretsApi[F[_]](
    httpClient: Client[F],
    config: KubeConfig,
    labels: Map[String, String] = Map.empty
)(
    implicit
    val F: Sync[F],
    val listDecoder: Decoder[SecretList],
    encoder: Encoder[Secret],
    decoder: Decoder[Secret]
) extends Listable[F, SecretList]
    with Filterable[SecretsApi[F]] {
  val resourceUri = uri"/api" / "v1" / "secrets"

  def namespace(namespace: String) = NamespacedSecretsApi(httpClient, config, namespace)

  override def withLabels(labels: Map[String, String]): SecretsApi[F] =
    SecretsApi(httpClient, config, labels)
}

private[client] case class NamespacedSecretsApi[F[_]](
    httpClient: Client[F],
    config: KubeConfig,
    namespace: String,
    labels: Map[String, String] = Map.empty
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
    with GroupDeletable[F]
    with Watchable[F, Secret]
    with Filterable[NamespacedSecretsApi[F]] {
  val resourceUri = uri"/api" / "v1" / "namespaces" / namespace / "secrets"

  def createEncode(resource: Secret): F[Status] = create(encode(resource))

  def createOrUpdateEncode(resource: Secret): F[Status] =
    createOrUpdate(encode(resource))

  private def encode(resource: Secret) =
    resource.copy(data = resource.data.map(_.view.mapValues(v => Base64.getEncoder.encodeToString(v.getBytes)).toMap))

  override def withLabels(labels: Map[String, String]): NamespacedSecretsApi[F] =
    NamespacedSecretsApi(httpClient, config, namespace, labels)
}
