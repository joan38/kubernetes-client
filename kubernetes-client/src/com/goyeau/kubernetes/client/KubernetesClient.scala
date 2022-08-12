package com.goyeau.kubernetes.client

import cats.data.OptionT
import cats.effect._
import com.goyeau.kubernetes.client.api._
import com.goyeau.kubernetes.client.crd.{CrdContext, CustomResource, CustomResourceList}
import com.goyeau.kubernetes.client.util.{CachedExecToken, SslContexts}
import io.circe.{Decoder, Encoder}
import org.http4s.client.Client
import org.http4s.jdkhttpclient.{JdkHttpClient, JdkWSClient, WSClient}
import org.typelevel.log4cats.Logger

import java.net.http.HttpClient

class KubernetesClient[F[_]: Async: Logger](
    httpClient: Client[F],
    wsClient: WSClient[F],
    config: KubeConfig,
    cachedExecToken: Option[CachedExecToken[F]]
) {
  lazy val namespaces: NamespacesApi[F] = new NamespacesApi(httpClient, config, cachedExecToken)
  lazy val pods: PodsApi[F] = new PodsApi(
    httpClient,
    wsClient,
    config,
    cachedExecToken
  )
  lazy val jobs: JobsApi[F]                       = new JobsApi(httpClient, config, cachedExecToken)
  lazy val cronJobs: CronJobsApi[F]               = new CronJobsApi(httpClient, config, cachedExecToken)
  lazy val deployments: DeploymentsApi[F]         = new DeploymentsApi(httpClient, config, cachedExecToken)
  lazy val statefulSets: StatefulSetsApi[F]       = new StatefulSetsApi(httpClient, config, cachedExecToken)
  lazy val replicaSets: ReplicaSetsApi[F]         = new ReplicaSetsApi(httpClient, config, cachedExecToken)
  lazy val services: ServicesApi[F]               = new ServicesApi(httpClient, config, cachedExecToken)
  lazy val serviceAccounts: ServiceAccountsApi[F] = new ServiceAccountsApi(httpClient, config, cachedExecToken)
  lazy val configMaps: ConfigMapsApi[F]           = new ConfigMapsApi(httpClient, config, cachedExecToken)
  lazy val secrets: SecretsApi[F]                 = new SecretsApi(httpClient, config, cachedExecToken)
  lazy val horizontalPodAutoscalers: HorizontalPodAutoscalersApi[F] = new HorizontalPodAutoscalersApi(
    httpClient,
    config,
    cachedExecToken
  )
  lazy val podDisruptionBudgets: PodDisruptionBudgetsApi[F] = new PodDisruptionBudgetsApi(
    httpClient,
    config,
    cachedExecToken
  )
  lazy val customResourceDefinitions: CustomResourceDefinitionsApi[F] = new CustomResourceDefinitionsApi(
    httpClient,
    config,
    cachedExecToken
  )
  lazy val ingresses: IngressessApi[F] = new IngressessApi(httpClient, config, cachedExecToken)
  lazy val leases: LeasesApi[F]        = new LeasesApi(httpClient, config, cachedExecToken)

  def customResources[A: Encoder: Decoder, B: Encoder: Decoder](context: CrdContext)(implicit
      listDecoder: Decoder[CustomResourceList[A, B]],
      encoder: Encoder[CustomResource[A, B]],
      decoder: Decoder[CustomResource[A, B]]
  ) = new CustomResourcesApi[F, A, B](httpClient, config, cachedExecToken, context)
}

object KubernetesClient {
  def apply[F[_]: Async: Logger](config: KubeConfig): Resource[F, KubernetesClient[F]] =
    for {
      client <- Resource.eval {
        Sync[F].delay(HttpClient.newBuilder().sslContext(SslContexts.fromConfig(config)).build())
      }
      httpClient      <- JdkHttpClient[F](client)
      wsClient        <- JdkWSClient[F](client)
      cachedExecToken <- Resource.eval(OptionT.fromOption(config.authInfoExec).semiflatMap(CachedExecToken[F]).value)
    } yield new KubernetesClient(
      httpClient,
      wsClient,
      config,
      cachedExecToken
    )

  def apply[F[_]: Async: Logger](config: F[KubeConfig]): Resource[F, KubernetesClient[F]] =
    Resource.eval(config).flatMap(apply(_))
}
