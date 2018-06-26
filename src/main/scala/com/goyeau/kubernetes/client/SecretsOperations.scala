package com.goyeau.kubernetes.client

import java.util.Base64

import scala.concurrent.{ExecutionContext, Future}

import akka.actor.ActorSystem
import io.circe._
import io.k8s.api.core.v1.{Secret, SecretList}

private[client] case class SecretsOperations(protected val config: KubeConfig)(
  implicit protected val system: ActorSystem,
  protected val listDecoder: Decoder[SecretList],
  encoder: Encoder[Secret],
  decoder: Decoder[Secret]
) extends Listable[SecretList] {
  protected val resourceUri = "api/v1/secrets"

  def namespace(namespace: String) = NamespacedSecretsOperations(config, namespace)
}

private[client] case class NamespacedSecretsOperations(
  protected val config: KubeConfig,
  protected val namespace: String
)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[Secret],
  protected val resourceDecoder: Decoder[Secret],
  protected val listDecoder: Decoder[SecretList]
) extends Creatable[Secret]
    with Replaceable[Secret]
    with Gettable[Secret]
    with Listable[SecretList]
    with Deletable
    with GroupDeletable {
  protected val resourceUri = s"api/v1/namespaces/$namespace/secrets"

  def createEncode(resource: Secret)(implicit ec: ExecutionContext): Future[Unit] = create(encode(resource))

  def createOrUpdateEncode(resource: Secret)(implicit ec: ExecutionContext): Future[Unit] =
    createOrUpdate(encode(resource))

  private def encode(resource: Secret) =
    resource.copy(data = resource.data.map(_.mapValues(v => Base64.getEncoder.encodeToString(v.getBytes))))
}
