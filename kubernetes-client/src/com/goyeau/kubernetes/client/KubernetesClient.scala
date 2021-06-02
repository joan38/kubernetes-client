package com.goyeau.kubernetes.client

import java.net.http.HttpClient

import cats.effect._
import com.goyeau.kubernetes.client.api._
import com.goyeau.kubernetes.client.crd.{CrdContext, CustomResource, CustomResourceList}
import com.goyeau.kubernetes.client.util.SslContexts
import io.circe.{Decoder, Encoder}
import org.http4s.client.Client
import org.http4s.jdkhttpclient.{WSClient, JdkHttpClient, JdkWSClient}

class KubernetesClient[F[_]: Sync](httpClient: Client[F], wsClient: WSClient[F], config: KubeConfig) {
  lazy val namespaces = new NamespacesApi(httpClient, config)
  lazy val pods = new PodsApi(
    httpClient,
    wsClient,
    config
  )
  lazy val jobs                      = new JobsApi(httpClient, config)
  lazy val cronJobs                  = new CronJobsApi(httpClient, config)
  lazy val deployments               = new DeploymentsApi(httpClient, config)
  lazy val statefulSets              = new StatefulSetsApi(httpClient, config)
  lazy val replicaSets               = new ReplicaSetsApi(httpClient, config)
  lazy val services                  = new ServicesApi(httpClient, config)
  lazy val serviceAccounts           = new ServiceAccountsApi(httpClient, config)
  lazy val configMaps                = new ConfigMapsApi(httpClient, config)
  lazy val secrets                   = new SecretsApi(httpClient, config)
  lazy val horizontalPodAutoscalers  = new HorizontalPodAutoscalersApi(httpClient, config)
  lazy val podDisruptionBudgets      = new PodDisruptionBudgetsApi(httpClient, config)
  lazy val customResourceDefinitions = new CustomResourceDefinitionsApi(httpClient, config)
  lazy val ingresses                 = new IngressessApi(httpClient, config)

  def customResources[A: Encoder: Decoder, B: Encoder: Decoder](context: CrdContext)(implicit
      listDecoder: Decoder[CustomResourceList[A, B]],
      encoder: Encoder[CustomResource[A, B]],
      decoder: Decoder[CustomResource[A, B]]
  ) = new CustomResourcesApi[F, A, B](httpClient, config, context)
}

object KubernetesClient {
  def apply[F[_]: ConcurrentEffect: ContextShift](config: KubeConfig): Resource[F, KubernetesClient[F]] =
    Resource.pure {
      val client = HttpClient.newBuilder().sslContext(SslContexts.fromConfig(config)).build()
      new KubernetesClient(
        JdkHttpClient(client),
        JdkWSClient[F](client),
        config
      )
    }

  def apply[F[_]: ConcurrentEffect: ContextShift](config: F[KubeConfig]): Resource[F, KubernetesClient[F]] =
    Resource.eval(config).flatMap(apply(_))
}
