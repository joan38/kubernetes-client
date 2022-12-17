package com.goyeau.kubernetes.client.api

import cats.effect.Async
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.operation.*
import io.circe.*
import io.k8s.api.policy.v1.{PodDisruptionBudget, PodDisruptionBudgetList}
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.implicits.*

private[client] class PodDisruptionBudgetsApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val authorization: Option[F[Authorization]]
)(implicit
    val F: Async[F],
    val listDecoder: Decoder[PodDisruptionBudgetList],
    val resourceDecoder: Decoder[PodDisruptionBudget],
    encoder: Encoder[PodDisruptionBudget]
) extends Listable[F, PodDisruptionBudgetList]
    with Watchable[F, PodDisruptionBudget] {
  val resourceUri: Uri = uri"/apis" / "policy" / "v1beta1" / "poddisruptionbudgets"

  def namespace(namespace: String): NamespacedPodDisruptionBudgetApi[F] =
    new NamespacedPodDisruptionBudgetApi(httpClient, config, authorization, namespace)
}

private[client] class NamespacedPodDisruptionBudgetApi[F[_]](
    val httpClient: Client[F],
    val config: KubeConfig[F],
    val authorization: Option[F[Authorization]],
    namespace: String
)(implicit
    val F: Async[F],
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
