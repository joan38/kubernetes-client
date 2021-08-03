package com.goyeau.kubernetes.client.api

import cats.effect.Async
import cats.kernel.Monoid
import cats.syntax.either._
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.api.ExecStream.{StdErr, StdOut}
import com.goyeau.kubernetes.client.operation._
import fs2.Pipe
import io.circe._
import io.circe.parser.decode
import io.k8s.api.core.v1.{Pod, PodList}
import io.k8s.apimachinery.pkg.apis.meta.v1.Status
import org.http4s._
import org.http4s.client.Client
import org.http4s.jdkhttpclient._
import org.http4s.implicits._
import scodec.bits.ByteVector
import org.typelevel.ci.CIString

private[client] class PodsApi[F[_]](val httpClient: Client[F], wsClient: WSClient[F], val config: KubeConfig)(implicit
    val F: Async[F],
    val listDecoder: Decoder[PodList],
    encoder: Encoder[Pod],
    decoder: Decoder[Pod]
) extends Listable[F, PodList] {
  val resourceUri: Uri = uri"/api" / "v1" / "pods"

  def namespace(namespace: String): NamespacedPodsApi[F] =
    new NamespacedPodsApi(httpClient, wsClient, config, namespace)
}

sealed trait ExecStream {
  def data: String
}
object ExecStream {
  final case class StdOut(data: String) extends ExecStream
  final case class StdErr(data: String) extends ExecStream
}

private[client] class NamespacedPodsApi[F[_]](
    val httpClient: Client[F],
    wsClient: WSClient[F],
    val config: KubeConfig,
    namespace: String
)(implicit
    val F: Async[F],
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
    with GroupDeletable[F]
    with Watchable[F, Pod] {
  val resourceUri: Uri = uri"/api" / "v1" / "namespaces" / namespace / "pods"

  val execHeaders: Headers =
    Headers(
      config.authorization.toList
    ).put(Header.Raw(CIString("Sec-WebSocket-Protocol"), "v4.channel.k8s.io"))

  val webSocketAddress: Uri = Uri.unsafeFromString(
    config.server
      .toString()
      .replaceFirst("http", "ws")
  )

  def exec[T: Monoid](
      podName: String,
      flow: ExecStream => T,
      container: Option[String] = None,
      command: Seq[String] = Seq.empty,
      stdin: Boolean = false,
      stdout: Boolean = true,
      stderr: Boolean = true,
      tty: Boolean = false
  ): F[(T, Either[String, Status])] = {
    val uri = (webSocketAddress.resolve(resourceUri) / podName / "exec")
      .+?("stdin" -> stdin.toString)
      .+?("stdout" -> stdout.toString)
      .+?("stderr" -> stderr.toString)
      .+?("tty" -> tty.toString)
      .+??("container" -> container)
      .++?("command" -> command)

    val request = WSRequest(uri, execHeaders, Method.POST)
    wsClient.connectHighLevel(request).use { connection =>
      connection.receiveStream
        .through(processWebSocketData)
        .compile
        .fold((Monoid.empty[T], "".asLeft[Status])) { case ((accEvents, accStatus), (events, status)) =>
          (
            events.foldLeft(accEvents) { case (acc, es) =>
              Monoid.combine[T](acc, flow(es))
            },
            accStatus.orElse(status)
          )
        }
    }
  }

  // first byte defines which stream incoming message belongs to
  private def processWebSocketData: Pipe[F, WSDataFrame, (List[ExecStream], Either[String, Status])] =
    _.collect {
      case WSFrame.Binary(data, _) if data.headOption.contains(1.toByte) =>
        List(StdOut(convertToString(dropRoutingByte(data)))) -> "".asLeft
      case WSFrame.Binary(data, _) if data.headOption.contains(2.toByte) =>
        List(StdErr(convertToString(dropRoutingByte(data)))) -> "".asLeft
      case WSFrame.Binary(data, _) if data.headOption.contains(3.toByte) =>
        val json = convertToString(dropRoutingByte(data))
        Nil -> decode[Status](json).left.flatMap(_ => json.asLeft[Status])
    }

  private def dropRoutingByte(data: ByteVector) =
    data.drop(1)

  private def convertToString(data: ByteVector) =
    new String(data.toArray, Charset.`UTF-8`.nioCharset)
}
