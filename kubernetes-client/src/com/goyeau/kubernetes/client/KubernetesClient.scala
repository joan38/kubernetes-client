package com.goyeau.kubernetes.client

import cats.data.OptionT
import cats.effect.*
import com.goyeau.kubernetes.client.api.*
import com.goyeau.kubernetes.client.crd.{CrdContext, CustomResource, CustomResourceList}
import com.goyeau.kubernetes.client.util.SslContexts
import com.goyeau.kubernetes.client.util.cache.{AuthorizationCache, ExecTokenCache, TokenCache}
import io.circe.{Decoder, Encoder}
import org.http4s.client.Client
import org.http4s.jdkhttpclient.{JdkHttpClient, JdkWSClient, WSClient}
import org.typelevel.log4cats.Logger

import java.net.http.HttpClient

class KubernetesClient[F[_]: Async: Logger](
    httpClient: Client[F],
    wsClient: WSClient[F],
    config: KubeConfig[F],
    authCache: Option[TokenCache[F]]
) {
  lazy val namespaces: NamespacesApi[F] = new NamespacesApi(httpClient, config, authCache)
  lazy val pods: PodsApi[F] = new PodsApi(
    httpClient,
    wsClient,
    config,
    authCache
  )
  lazy val jobs: JobsApi[F]                       = new JobsApi(httpClient, config, authCache)
  lazy val cronJobs: CronJobsApi[F]               = new CronJobsApi(httpClient, config, authCache)
  lazy val deployments: DeploymentsApi[F]         = new DeploymentsApi(httpClient, config, authCache)
  lazy val statefulSets: StatefulSetsApi[F]       = new StatefulSetsApi(httpClient, config, authCache)
  lazy val replicaSets: ReplicaSetsApi[F]         = new ReplicaSetsApi(httpClient, config, authCache)
  lazy val services: ServicesApi[F]               = new ServicesApi(httpClient, config, authCache)
  lazy val serviceAccounts: ServiceAccountsApi[F] = new ServiceAccountsApi(httpClient, config, authCache)
  lazy val configMaps: ConfigMapsApi[F]           = new ConfigMapsApi(httpClient, config, authCache)
  lazy val secrets: SecretsApi[F]                 = new SecretsApi(httpClient, config, authCache)
  lazy val horizontalPodAutoscalers: HorizontalPodAutoscalersApi[F] = new HorizontalPodAutoscalersApi(
    httpClient,
    config,
    authCache
  )
  lazy val podDisruptionBudgets: PodDisruptionBudgetsApi[F] = new PodDisruptionBudgetsApi(
    httpClient,
    config,
    authCache
  )
  lazy val customResourceDefinitions: CustomResourceDefinitionsApi[F] = new CustomResourceDefinitionsApi(
    httpClient,
    config,
    authCache
  )
  lazy val ingresses: IngressessApi[F] = new IngressessApi(httpClient, config, authCache)
  lazy val leases: LeasesApi[F]        = new LeasesApi(httpClient, config, authCache)

  def customResources[A: Encoder: Decoder, B: Encoder: Decoder](context: CrdContext)(implicit
      listDecoder: Decoder[CustomResourceList[A, B]],
      encoder: Encoder[CustomResource[A, B]],
      decoder: Decoder[CustomResource[A, B]]
  ) = new CustomResourcesApi[F, A, B](httpClient, config, authCache, context)
}

object KubernetesClient {
  def apply[F[_]: Async: Logger](config: KubeConfig[F]): Resource[F, KubernetesClient[F]] =
    for {
      client <- Resource.eval {
        Sync[F].delay(HttpClient.newBuilder().sslContext(SslContexts.fromConfig(config)).build())
      }
      httpClient <- JdkHttpClient[F](client)
      wsClient   <- JdkWSClient[F](client)
      authCache <- Resource.eval(
        OptionT
          .fromOption(config.authorization)
          .semiflatMap(AuthorizationCache(_, config.refreshTokenBeforeExpiration))
          .orElse {
            OptionT
              .fromOption(config.authInfoExec)
              .semiflatMap(ExecTokenCache[F](_, config.refreshTokenBeforeExpiration))
          }
          .value
      )
    } yield new KubernetesClient(
      httpClient,
      wsClient,
      config,
      authCache
    )

  def apply[F[_]: Async: Logger](config: F[KubeConfig[F]]): Resource[F, KubernetesClient[F]] =
    Resource.eval(config).flatMap(apply(_))
}
