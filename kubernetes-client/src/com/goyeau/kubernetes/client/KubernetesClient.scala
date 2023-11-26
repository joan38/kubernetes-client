package com.goyeau.kubernetes.client

import cats.syntax.all.*
import cats.data.OptionT
import cats.effect.*
import com.goyeau.kubernetes.client.api.*
import com.goyeau.kubernetes.client.crd.{CrdContext, CustomResource, CustomResourceList}
import com.goyeau.kubernetes.client.util.cache.{AuthorizationParse, ExecToken}
import io.circe.{Decoder, Encoder}
import org.http4s.client.Client
import org.http4s.client.websocket.WSClient
import org.http4s.headers.Authorization
import org.typelevel.log4cats.Logger
import fs2.io.process.Processes
import fs2.io.file.Files
import fs2.io.net.Network

class KubernetesClient[F[_]: Async: Files: Logger](
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
  lazy val nodes: NodesApi[F]          = new NodesApi(httpClient, config, authorization)
  lazy val raw: RawApi[F]              = new RawApi[F](httpClient, wsClient, config, authorization)

  def customResources[A: Encoder: Decoder, B: Encoder: Decoder](context: CrdContext)(implicit
      listDecoder: Decoder[CustomResourceList[A, B]],
      encoder: Encoder[CustomResource[A, B]],
      decoder: Decoder[CustomResource[A, B]]
  ) = new CustomResourcesApi[F, A, B](httpClient, config, authorization, context)

}

object KubernetesClient {

  def apply[F[_]: Async: Logger: Files: Network: Processes](config: KubeConfig[F]): Resource[F, KubernetesClient[F]] =
    for {
      clients <- PlatformSpecific.clients(config)
      authorization <- Resource.eval {
        OptionT
          .fromOption(config.authorization)
          // if the authorization is provided directly, we try to parse it as a JWT
          // in order to get the expiration time
          .map(AuthorizationParse(_))
          .orElse {
            OptionT
              .fromOption(config.authInfoExec)
              // if the authorization is provided via the auth plugin, we execute the plugin
              // and get the expiration time along the token itself from the output of the plugin
              .map(ExecToken(_))
          }
          .semiflatMap { authorization =>
            config.authorizationCache
              // if authorizationCache is provided, we "wrap" the authorization using it
              .mapApply(authorization)
              // otherwise, we use the authorization as is and ignore the expiration time
              .getOrElse(
                authorization.map(_.authorization).pure
              )
          }
          .value
      }
    } yield new KubernetesClient(
      clients.httpClient,
      clients.wsClient,
      config,
      authorization
    )

  def apply[F[_]: Async: Files: Logger: Network: Processes](
      config: F[KubeConfig[F]]
  ): Resource[F, KubernetesClient[F]] =
    Resource.eval(config).flatMap(apply(_))
}
