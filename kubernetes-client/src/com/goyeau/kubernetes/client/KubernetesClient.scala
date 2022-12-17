package com.goyeau.kubernetes.client

import cats.syntax.all.*
import cats.data.OptionT
import cats.effect.*
import com.goyeau.kubernetes.client.api.*
import com.goyeau.kubernetes.client.crd.{CrdContext, CustomResource, CustomResourceList}
import com.goyeau.kubernetes.client.util.SslContexts
import com.goyeau.kubernetes.client.util.cache.{AuthorizationParse, ExecToken}
import io.circe.{Decoder, Encoder}
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.jdkhttpclient.{JdkHttpClient, JdkWSClient, WSClient}
import org.typelevel.log4cats.Logger

import java.net.http.HttpClient

class KubernetesClient[F[_]: Async: Logger](
    httpClient: Client[F],
    wsClient: WSClient[F],
    config: KubeConfig[F],
    authorization: Option[F[Authorization]]
) {
  lazy val namespaces: NamespacesApi[F] = new NamespacesApi(httpClient, config, authorization)
  lazy val pods: PodsApi[F] = new PodsApi(
    httpClient,
    wsClient,
    config,
    authorization
  )
  lazy val jobs: JobsApi[F]                       = new JobsApi(httpClient, config, authorization)
  lazy val cronJobs: CronJobsApi[F]               = new CronJobsApi(httpClient, config, authorization)
  lazy val deployments: DeploymentsApi[F]         = new DeploymentsApi(httpClient, config, authorization)
  lazy val statefulSets: StatefulSetsApi[F]       = new StatefulSetsApi(httpClient, config, authorization)
  lazy val replicaSets: ReplicaSetsApi[F]         = new ReplicaSetsApi(httpClient, config, authorization)
  lazy val services: ServicesApi[F]               = new ServicesApi(httpClient, config, authorization)
  lazy val serviceAccounts: ServiceAccountsApi[F] = new ServiceAccountsApi(httpClient, config, authorization)
  lazy val configMaps: ConfigMapsApi[F]           = new ConfigMapsApi(httpClient, config, authorization)
  lazy val secrets: SecretsApi[F]                 = new SecretsApi(httpClient, config, authorization)
  lazy val horizontalPodAutoscalers: HorizontalPodAutoscalersApi[F] = new HorizontalPodAutoscalersApi(
    httpClient,
    config,
    authorization
  )
  lazy val podDisruptionBudgets: PodDisruptionBudgetsApi[F] = new PodDisruptionBudgetsApi(
    httpClient,
    config,
    authorization
  )
  lazy val customResourceDefinitions: CustomResourceDefinitionsApi[F] = new CustomResourceDefinitionsApi(
    httpClient,
    config,
    authorization
  )
  lazy val ingresses: IngressessApi[F] = new IngressessApi(httpClient, config, authorization)
  lazy val leases: LeasesApi[F]        = new LeasesApi(httpClient, config, authorization)

  def customResources[A: Encoder: Decoder, B: Encoder: Decoder](context: CrdContext)(implicit
      listDecoder: Decoder[CustomResourceList[A, B]],
      encoder: Encoder[CustomResource[A, B]],
      decoder: Decoder[CustomResource[A, B]]
  ) = new CustomResourcesApi[F, A, B](httpClient, config, authorization, context)
}

object KubernetesClient {
  def apply[F[_]: Async: Logger](config: KubeConfig[F]): Resource[F, KubernetesClient[F]] =
    for {
      client <- Resource.eval {
        Sync[F].delay(HttpClient.newBuilder().sslContext(SslContexts.fromConfig(config)).build())
      }
      httpClient <- JdkHttpClient[F](client)
      wsClient   <- JdkWSClient[F](client)
      authorization <- Resource.eval {
        OptionT
          .fromOption(config.authorization)
          .map(AuthorizationParse(_))
          .orElse {
            OptionT
              .fromOption(config.authInfoExec)
              .map(ExecToken(_))
          }
          .semiflatMap { authorization =>
            config.authorizationCache.mapApply(authorization).getOrElse(authorization.map(_.authorization).pure)
          }
          .value
      }
    } yield new KubernetesClient(
      httpClient,
      wsClient,
      config,
      authorization
    )

  def apply[F[_]: Async: Logger](config: F[KubeConfig[F]]): Resource[F, KubernetesClient[F]] =
    Resource.eval(config).flatMap(apply(_))
}
