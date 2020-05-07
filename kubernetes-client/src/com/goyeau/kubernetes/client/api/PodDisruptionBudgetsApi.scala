package com.goyeau.kubernetes.client.api

import cats.effect.Sync
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation._
import io.circe._
import io.k8s.api.policy.v1beta1.{PodDisruptionBudget, PodDisruptionBudgetList}
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.implicits._

private[client] case class PodDisruptionBudgetsApi[F[_]](httpClient: Client[F], config: KubeConfig)(implicit
    val F: Sync[F],
    val listDecoder: Decoder[PodDisruptionBudgetList],
    encoder: Encoder[PodDisruptionBudget],
    decoder: Decoder[PodDisruptionBudget]
) extends Listable[F, PodDisruptionBudgetList] {
  val resourceUri: Uri = uri"/apis" / "policy" / "v1beta1" / "poddisruptionbudgets"

  def namespace(namespace: String): NamespacedPodDisruptionBudgetApi[F] =
    NamespacedPodDisruptionBudgetApi(httpClient, config, namespace)
}

private[client] case class NamespacedPodDisruptionBudgetApi[F[_]](
    httpClient: Client[F],
    config: KubeConfig,
    namespace: String
)(implicit
    val F: Sync[F],
    val resourceEncoder: Encoder[PodDisruptionBudget],
    val resourceDecoder: Decoder[PodDisruptionBudget],
    val listDecoder: Decoder[PodDisruptionBudgetList]
) extends Creatable[F, PodDisruptionBudget]
    with Replaceable[F, PodDisruptionBudget]
    with Gettable[F, PodDisruptionBudget]
    with Listable[F, PodDisruptionBudgetList]
    with Deletable[F]
    with GroupDeletable[F]
    with Watchable[F, PodDisruptionBudget] {
  val resourceUri: Uri = uri"/apis" / "policy" / "v1beta1" / "namespaces" / namespace / "poddisruptionbudgets"
}
