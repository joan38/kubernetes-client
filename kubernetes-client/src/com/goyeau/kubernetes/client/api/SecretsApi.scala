package com.goyeau.kubernetes.client.api

import cats.syntax.all.*
import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation.*
import io.circe.*
import io.k8s.api.core.v1.{Secret, SecretList}
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.implicits.*
import org.http4s.{Status, Uri}

private[client] class SecretsApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val authorization: Option[F[Authorization]]
)(implicit
    val F: Async[F],
    val listDecoder: Decoder[SecretList],
    val resourceDecoder: Decoder[Secret],
    encoder: Encoder[Secret]
) extends Listable[F, SecretList]
    with Watchable[F, Secret] {
  val resourceUri = uri"/api" / "v1" / "secrets"

  def namespace(namespace: String) = new NamespacedSecretsApi(httpClient, config, authorization, namespace)
}

private[client] class NamespacedSecretsApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val authorization: Option[F[Authorization]],
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

  def createEncode(resource: Secret): F[Status] = encode(resource).flatMap(create)

  def createOrUpdateEncode(resource: Secret): F[Status] =
    encode(resource).flatMap(createOrUpdate)

  private def encode(resource: Secret): F[Secret] = {
    resource.data.fold(
      resource.pure[F]
    ) { data => 
      data.toSeq.traverse { case (k, v) => 
        fs2.Stream.emit(v).covary[F]
          .through(fs2.text.utf8.encode)
          .through(fs2.text.base64.encode)
          .foldMonoid
          .compile
          .lastOrError
          .map { encoded => 
            k -> encoded
          }
      }.map { encoded => resource.copy(data = encoded.toMap.some) }
    }  
  }

}
