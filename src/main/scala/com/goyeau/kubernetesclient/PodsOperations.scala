package com.goyeau.kubernetesclient

import java.io.IOException
import java.net.URLEncoder

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{StatusCodes, Uri}
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
  protected val resourceDecoder: Decoder[PodList],
  encoder: Encoder[Pod],
  decoder: Decoder[Pod]
) extends Listable[PodList] {
  protected val resourceUri = s"${config.server}/api/v1/pods"

  def namespace(namespace: String) = NamespacedPodsOperations(config, namespace)
}

private[kubernetesclient] case class NamespacedPodsOperations(protected val config: KubeConfig,
                                                              protected val namespace: String)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[Pod],
  decoder: Decoder[Pod],
  protected val resourceDecoder: Decoder[PodList]
) extends Creatable[Pod]
    with CreateOrUpdatable[Pod]
    with Listable[PodList]
    with GroupDeletable {
  protected val resourceUri = s"${config.server}/api/v1/namespaces/$namespace/pods"

  def apply(podName: String) = PodOperations(config, s"$resourceUri/$podName")
}

private[kubernetesclient] case class PodOperations(protected val config: KubeConfig, protected val resourceUri: Uri)(
  implicit protected val system: ActorSystem,
  protected val resourceEncoder: Encoder[Pod],
  protected val resourceDecoder: Decoder[Pod]
) extends Gettable[Pod]
    with Replaceable[Pod]
    with Deletable {

  def exec[Result](flow: Flow[Either[Status, String], Message, Future[Result]],
                   container: Option[String] = None,
                   command: Seq[String] = Seq.empty,
                   stdin: Boolean = false,
                   stdout: Boolean = true,
                   stderr: Boolean = true,
                   tty: Boolean = false)(implicit system: ActorSystem): Future[Result] = {
    implicit val materializer: Materializer = ActorMaterializer()
    implicit val ec: ExecutionContext = system.dispatcher
    val containerParam = container.fold("")(containerName => s"&container=$containerName")
    val commandParam = command.map(c => s"&command=${URLEncoder.encode(c, "UTF-8")}").mkString
    val execParams = s"stdin=$stdin&stdout=$stdout&stderr=$stderr&tty=$tty$containerParam$commandParam"
    val execUri = s"${resourceUri.toString.replaceFirst("http", "ws")}/exec?$execParams"

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

    val (upgradeResponse, closed) = Http().singleWebSocketRequest(
      WebSocketRequest(
        execUri,
        extraHeaders = config.oauthToken.toList.map(token => Authorization(OAuth2BearerToken(token))),
        subprotocol = Option("v4.channel.k8s.io")
      ),
      flow.join(mapFlow),
      SecurityUtils.httpsConnectionContext(config),
      settings = ClientConnectionSettings(system).withIdleTimeout(10.minutes)
    )

    upgradeResponse.map(_.response.status).flatMap {
      case StatusCodes.SwitchingProtocols => closed
      case status                         => throw new IOException(s"Connection to Kubernetes API failed: $status")
    }
  }
}
