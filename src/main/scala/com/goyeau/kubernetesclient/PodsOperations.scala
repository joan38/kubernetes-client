package com.goyeau.kubernetesclient

import java.io.IOException
import java.net.URLEncoder

import scala.concurrent.{ExecutionContext, Future}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.ws.{Message, WebSocketRequest}
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import com.typesafe.scalalogging.LazyLogging
import io.circe._
import io.circe.generic.auto._
import io.k8s.api.core.v1.Pod

private[kubernetesclient] case class PodsOperations(config: KubeConfig, private val namespace: String)(
  implicit val system: ActorSystem,
  val encoder: Encoder[Pod]
) extends Creatable[Pod]
    with GroupDeletable {
  val resourceUri = s"${config.server}/api/v1/namespaces/$namespace/pods"

  def apply(podName: String) = PodOperations(config, s"$resourceUri/$podName")
}

private[kubernetesclient] case class PodOperations(config: KubeConfig, resourceUri: Uri)(
  implicit val system: ActorSystem,
  val decoder: Decoder[Pod],
  val encoder: Encoder[Pod]
) extends Gettable[Pod]
    with Replaceable[Pod]
    with Deletable
    with LazyLogging {

  def exec[Result](flow: Flow[Message, Message, Future[Result]],
                   container: Option[String] = None,
                   command: Seq[String] = Seq.empty,
                   stdin: Boolean = false,
                   stdout: Boolean = true,
                   stderr: Boolean = true,
                   tty: Boolean = false)(implicit ec: ExecutionContext, mat: Materializer): Future[Result] = {
    val containerParam = container.fold("")(containerName => s"&container=$containerName")
    val commandParam = command.map(c => s"&command=${URLEncoder.encode(c, "UTF-8")}").mkString
    val execParams = s"stdin=$stdin&stdout=$stdout&stderr=$stderr&tty=$tty$containerParam$commandParam"
    val execUri = s"${resourceUri.toString.replace("http", "ws")}/exec?$execParams"

    val (upgradeResponse, closed) = Http().singleWebSocketRequest(
      WebSocketRequest(
        execUri,
        extraHeaders = config.oauthToken.toList.map(token => Authorization(OAuth2BearerToken(token))),
        subprotocol = Option("v4.channel.k8s.io")
      ),
      flow,
      SecurityUtils.httpsConnectionContext(config)
    )

    upgradeResponse.map(_.response.status).flatMap {
      case StatusCodes.SwitchingProtocols => closed
      case status                         => throw new IOException(s"Connection to Kubernetes API failed: $status")
    }
  }
}
