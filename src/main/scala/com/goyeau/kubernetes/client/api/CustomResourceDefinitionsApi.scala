package com.goyeau.kubernetes.client.api

import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import io.circe.{Decoder, Encoder}
import io.k8s.apiextensionsapiserver.pkg.apis.apiextensions.v1.{CustomResourceDefinition, CustomResourceDefinitionList}
import org.http4s.client.Client
import org.http4s.implicits._

private[client] case class CustomResourceDefinitionsApi[F[_]](
    httpClient: Client[F],
    config: KubeConfig,
    labels: Map[String, String] = Map.empty
)(
    implicit
    val F: Sync[F],
    val listDecoder: Decoder[CustomResourceDefinitionList],
    val resourceEncoder: Encoder[CustomResourceDefinition],
    val resourceDecoder: Decoder[CustomResourceDefinition]
) extends Creatable[F, CustomResourceDefinition]
    with Replaceable[F, CustomResourceDefinition]
    with Gettable[F, CustomResourceDefinition]
    with Listable[F, CustomResourceDefinitionList]
    with Deletable[F]
    with DeletableTerminated[F]
    with GroupDeletable[F]
    with Watchable[F, CustomResourceDefinition]
    with Filterable[CustomResourceDefinitionsApi[F]] { self =>
  val resourceUri               = uri"/apis" / "apiextensions.k8s.io" / "v1" / "customresourcedefinitions"
  override val watchResourceUri = uri"/apis" / "apiextensions.k8s.io" / "v1" / "watch" / "customresourcedefinitions"

  def withLabels(labels: Map[String, String]): CustomResourceDefinitionsApi[F] =
    CustomResourceDefinitionsApi(httpClient, config, labels)
}
