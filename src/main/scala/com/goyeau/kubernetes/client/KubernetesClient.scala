package com.goyeau.kubernetes.client

import cats.effect._
import com.goyeau.kubernetes.client.api._
import com.goyeau.kubernetes.client.crd.{CrdContext, CustomResource, CustomResourceList}
import com.goyeau.kubernetes.client.util.SslContexts
import io.circe.{Decoder, Encoder}
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext

case class KubernetesClient[F[_]: ConcurrentEffect](httpClient: Client[F], config: KubeConfig) {
  lazy val namespaces                = NamespacesApi(httpClient, config)
  lazy val pods                      = PodsApi(httpClient, config)
  lazy val jobs                      = JobsApi(httpClient, config)
  lazy val cronJobs                  = CronJobsApi(httpClient, config)
  lazy val deployments               = DeploymentsApi(httpClient, config)
  lazy val statefulSets              = StatefulSetsApi(httpClient, config)
  lazy val replicaSets               = ReplicaSetsApi(httpClient, config)
  lazy val services                  = ServicesApi(httpClient, config)
  lazy val serviceAccounts           = ServiceAccountsApi(httpClient, config)
  lazy val configMaps                = ConfigMapsApi(httpClient, config)
  lazy val secrets                   = SecretsApi(httpClient, config)
  lazy val horizontalPodAutoscalers  = HorizontalPodAutoscalersApi(httpClient, config)
  lazy val podDisruptionBudgets      = PodDisruptionBudgetsApi(httpClient, config)
  lazy val customResourceDefinitions = CustomResourceDefinitionsApi(httpClient, config)

  def customResources[A: Encoder: Decoder, B: Encoder: Decoder](
      context: CrdContext
  )(
      implicit listDecoder: Decoder[CustomResourceList[A, B]],
      encoder: Encoder[CustomResource[A, B]],
      decoder: Decoder[CustomResource[A, B]]
  ) =
    CustomResourcesApi[F, A, B](httpClient, config, context)
}

object KubernetesClient {
  def apply[F[_]: ConcurrentEffect](config: KubeConfig): Resource[F, KubernetesClient[F]] =
    BlazeClientBuilder[F](ExecutionContext.global, Option(SslContexts.fromConfig(config))).resource
      .map(httpClient => apply(httpClient, config))

  def apply[F[_]: ConcurrentEffect](config: F[KubeConfig]): Resource[F, KubernetesClient[F]] =
    Resource.liftF(config).flatMap(apply(_))
}
