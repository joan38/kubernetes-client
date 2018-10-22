package com.goyeau.kubernetes.client.api

import java.net.URLEncoder

import scala.concurrent.duration._
import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, WebSocketRequest}
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.BidiFlow
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import cats.effect.{Async, IO, Timer}
import com.goyeau.kubernetes.client.operation._
import com.goyeau.kubernetes.client.util.SslContexts
import com.goyeau.kubernetes.client.{KubeConfig, KubernetesException}
import io.circe._
import io.circe.parser.decode
import io.k8s.api.core.v1.{Pod, PodList}
import io.k8s.apimachinery.pkg.apis.meta.v1.Status
import org.http4s
import org.http4s.AuthScheme
import org.http4s.Credentials.Token
import org.http4s.client.Client
import org.http4s.Uri.uri

private[client] case class PodsApi[F[_]](httpClient: Client[F], config: KubeConfig)(
  implicit
  val F: Async[F],
  timer: Timer[F],
  val listDecoder: Decoder[PodList],
  encoder: Encoder[Pod],
  decoder: Decoder[Pod]
) extends Listable[F, PodList] {
  val resourceUri = uri("/api") / "v1" / "pods"

  def namespace(namespace: String) = NamespacedPodsApi(httpClient, config, namespace)
}

private[client] case class NamespacedPodsApi[F[_]](
  httpClient: Client[F],
  config: KubeConfig,
  namespace: String
)(
  implicit
  val F: Async[F],
  val timer: Timer[F],
  val resourceEncoder: Encoder[Pod],
  val resourceDecoder: Decoder[Pod],
  val listDecoder: Decoder[PodList]
) extends Creatable[F, Pod]
    with Replaceable[F, Pod]
    with Gettable[F, Pod]
    with Listable[F, PodList]
    with Proxy[F]
    with Deletable[F]
    with DeletableTerminated[F]
    with GroupDeletable[F] {
  val resourceUri = uri("/api") / "v1" / "namespaces" / namespace / "pods"

  def exec[Result](
    podName: String,
    flow: Flow[Either[Status, String], Message, Future[Result]],
    container: Option[String] = None,
    command: Seq[String] = Seq.empty,
    stdin: Boolean = false,
    stdout: Boolean = true,
    stderr: Boolean = true,
    tty: Boolean = false
  )(implicit actorSystem: ActorSystem): F[Result] = {
    implicit val materializer: Materializer = ActorMaterializer()
    import actorSystem.dispatcher

    IO.fromFuture(IO {
        val containerParam = container.fold("")(containerName => s"&container=$containerName")
        val commandParam = command.map(c => s"&command=${URLEncoder.encode(c, "UTF-8")}").mkString
        val params = s"stdin=$stdin&stdout=$stdout&stderr=$stderr&tty=$tty$containerParam$commandParam"
        val uri = s"${config.server.toString.replaceFirst("http", "ws")}/$resourceUri/$podName/exec?$params"

        val mapFlow = BidiFlow.fromFlows(
          Flow.fromFunction[Message, Message](identity),
          Flow
            .fromFunction[Message, Future[String]] {
              case BinaryMessage.Strict(data)     => Future.successful(data.utf8String)
              case BinaryMessage.Streamed(stream) => stream.runFold(ByteString(""))(_ ++ _).map(_.utf8String)
              case TextMessage.Strict(data)       => Future.successful(data)
              case TextMessage.Streamed(stream)   => stream.runFold("")(_ + _)
            }
            .mapAsync(parallelism = 1)(identity)
            .map(log => decode[Status](log.trim).fold(_ => Right(log), Left(_)))
        )

        def convertAuthorization(authorization: http4s.headers.Authorization) = authorization.credentials match {
          case Token(AuthScheme.Bearer, token) => Authorization(OAuth2BearerToken(token))
          case _                               => throw new IllegalStateException("")
        }

        val (upgradeResponse, eventualResult) = Http().singleWebSocketRequest(
          WebSocketRequest(
            uri,
            extraHeaders = config.authorization.toList.map(convertAuthorization),
            subprotocol = Option("v4.channel.k8s.io")
          ),
          flow.join(mapFlow),
          ConnectionContext.https(SslContexts.fromConfig(config)),
          settings = ClientConnectionSettings(actorSystem).withIdleTimeout(10.minutes)
        )

        for {
          upgrade <- upgradeResponse
          response = upgrade.response
          entity <- response.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
          _ = if (response.status.isFailure)
            throw KubernetesException(response.status.intValue, uri, entity.utf8String)
          result <- eventualResult
        } yield result
      })
      .to[F]
  }
}
