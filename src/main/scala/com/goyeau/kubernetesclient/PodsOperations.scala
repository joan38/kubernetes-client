package com.goyeau.kubernetesclient

import java.net.URLEncoder

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, WebSocketRequest}
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.BidiFlow
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import io.circe._
import io.circe.generic.auto._
import io.circe.parser.decode
import io.k8s.api.core.v1.{Pod, PodList}
import io.k8s.apimachinery.pkg.apis.meta.v1.Status

private[kubernetesclient] case class PodsOperations(protected val config: KubeConfig)(
  implicit protected val system: ActorSystem,
  protected val listDecoder: Decoder[PodList],
  encoder: Encoder[Pod],
  decoder: Decoder[Pod]
) extends Listable[PodList] {
  protected val resourceUri = "api/v1/pods"

  def namespace(namespace: String) = NamespacedPodsOperations(config, namespace)
}

private[kubernetesclient] case class NamespacedPodsOperations(
  protected val config: KubeConfig,
  protected val namespace: String
)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[Pod],
  protected val resourceDecoder: Decoder[Pod],
  protected val listDecoder: Decoder[PodList]
) extends Creatable[Pod]
    with Replaceable[Pod]
    with Gettable[Pod]
    with Listable[PodList]
    with Proxy
    with Deletable
    with GroupDeletable {
  protected val resourceUri = s"api/v1/namespaces/$namespace/pods"

  def exec[Result](
    podName: String,
    flow: Flow[Either[Status, String], Message, Future[Result]],
    container: Option[String] = None,
    command: Seq[String] = Seq.empty,
    stdin: Boolean = false,
    stdout: Boolean = true,
    stderr: Boolean = true,
    tty: Boolean = false
  )(implicit ec: ExecutionContext): Future[Result] = {
    implicit val materializer: Materializer = ActorMaterializer()
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

    val (upgradeResponse, eventualResult) = Http().singleWebSocketRequest(
      WebSocketRequest(
        uri,
        extraHeaders = config.oauthToken.toList.map(token => Authorization(OAuth2BearerToken(token))),
        subprotocol = Option("v4.channel.k8s.io")
      ),
      flow.join(mapFlow),
      SecurityUtils.httpsConnectionContext(config),
      settings = ClientConnectionSettings(system).withIdleTimeout(10.minutes)
    )

    for {
      upgrade <- upgradeResponse
      response = upgrade.response
      entity <- response.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
      _ = if (response.status.isFailure)
        throw KubernetesException(response.status.intValue, uri, entity.utf8String)
      result <- eventualResult
    } yield result
  }
}
