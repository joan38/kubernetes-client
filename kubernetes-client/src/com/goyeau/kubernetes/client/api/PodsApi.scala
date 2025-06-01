package com.goyeau.kubernetes.client.api

import cats.effect.{Async, Resource}
import cats.syntax.all.*
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.api.ExecRouting.*
import com.goyeau.kubernetes.client.api.ExecStream.{StdErr, StdOut}
import com.goyeau.kubernetes.client.api.NamespacedPodsApi.ErrorOrStatus
import com.goyeau.kubernetes.client.operation.*
import fs2.concurrent.SignallingRef
import fs2.io.file.{Files, Flags, Path}
import fs2.{Chunk, Pipe, Stream}
import io.circe.*
import io.circe.parser.decode
import io.k8s.api.core.v1.{Pod, PodList}
import io.k8s.apimachinery.pkg.apis.meta.v1.Status
import org.http4s.*
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.implicits.*
import org.http4s.jdkhttpclient.*
import org.typelevel.ci.CIString
import org.typelevel.log4cats.Logger
import scodec.bits.ByteVector

import java.nio.file.Path as JPath
import scala.concurrent.duration.DurationInt

private[client] class PodsApi[F[_]: Logger](
    val httpClient: Client[F],
    wsClient: WSClient[F],
    val config: KubeConfig[F],
    val authorization: Option[F[Authorization]]
)(implicit
    val F: Async[F],
    val listDecoder: Decoder[PodList],
    val resourceDecoder: Decoder[Pod],
    encoder: Encoder[Pod]
) extends Listable[F, PodList]
    with Watchable[F, Pod] {
  val resourceUri: Uri = uri"/api" / "v1" / "pods"

  def namespace(namespace: String): NamespacedPodsApi[F] =
    new NamespacedPodsApi(httpClient, wsClient, config, authorization, namespace)
}

sealed trait ExecStream {
  def data: Array[Byte]
  def asString: String =
    new String(data, Charset.`UTF-8`.nioCharset)
}
object ExecStream {
  final case class StdOut(data: Array[Byte]) extends ExecStream
  final case class StdErr(data: Array[Byte]) extends ExecStream
}

final case class ParseFailure(status: String)

object NamespacedPodsApi {
  type ErrorOrStatus = Either[ParseFailure, Status]
}

private[client] object ExecRouting {
  val StdInId: Byte  = 0.byteValue
  val StdOutId: Byte = 1.byteValue
  val StdErrId: Byte = 2.byteValue
  val StatusId: Byte = 3.byteValue
}

private[client] class NamespacedPodsApi[F[_]](
    val httpClient: Client[F],
    wsClient: WSClient[F],
    val config: KubeConfig[F],
    val authorization: Option[F[Authorization]],
    namespace: String
)(implicit
    val F: Async[F],
    val resourceEncoder: Encoder[Pod],
    val resourceDecoder: Decoder[Pod],
    val listDecoder: Decoder[PodList],
    val logger: Logger[F]
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

  private val webSocketAddress: Uri = Uri.unsafeFromString(
    config.server
      .toString()
      .replaceFirst("http", "ws")
  )

  private def execRequest(
      podName: String,
      commands: Seq[String],
      container: Option[String],
      stdin: Boolean = false,
      stdout: Boolean = true,
      stderr: Boolean = true,
      tty: Boolean = false
  ): F[WSRequest] = {
    val uri = (webSocketAddress.resolve(resourceUri) / podName / "exec") +?
      ("stdin"     -> stdin.toString) +?
      ("stdout"    -> stdout.toString) +?
      ("stderr"    -> stderr.toString) +?
      ("tty"       -> tty.toString) +??
      ("container" -> container) ++?
      ("command"   -> commands)

    WSRequest(uri, method = Method.POST)
      .withOptionalAuthorization(authorization)
      .map { r =>
        r.copy(
          headers = r.headers.put(Header.Raw(CIString("Sec-WebSocket-Protocol"), "v4.channel.k8s.io"))
        )
      }
  }

  @deprecated("Use download() which uses fs2.io.file.Path", "0.8.2")
  def downloadFile(
      podName: String,
      sourceFile: JPath,
      destinationFile: JPath,
      container: Option[String] = None
  ): F[(List[StdErr], Option[ErrorOrStatus])] =
    download(podName, Path.fromNioPath(sourceFile), Path.fromNioPath(destinationFile), container)

  def download(
      podName: String,
      sourceFile: Path,
      destinationFile: Path,
      container: Option[String] = None
  ): F[(List[StdErr], Option[ErrorOrStatus])] =
    (
      F.ref(List.empty[StdErr]),
      F.ref(none[ErrorOrStatus])
    ).tupled.flatMap { case (stdErr, errorOrStatus) =>
      execRequest(podName, Seq("sh", "-c", s"cat ${sourceFile.toString}"), container).flatMap { request =>
        wsClient.connectHighLevel(request).use { connection =>
          connection.receiveStream
            .through(processWebSocketData)
            .evalMapFilter[F, Chunk[Byte]] {
              case Left(StdOut(data))   => Chunk.array(data).some.pure[F]
              case Left(e: StdErr)      => stdErr.update(e :: _).as(None)
              case Right(statusOrError) => errorOrStatus.update(_.orElse(statusOrError.some)).as(None)
            }
            .unchunks
            .through(Files[F].writeAll(destinationFile))
            .compile
            .drain
            .flatMap { _ =>
              (stdErr.get.map(_.reverse), errorOrStatus.get).tupled
            }
        }
      }
    }

  @deprecated("Use upload() which uses fs2.io.file.Path", "0.8.2")
  def uploadFile(
      podName: String,
      sourceFile: JPath,
      destinationFile: JPath,
      container: Option[String] = None
  ): F[(List[StdErr], Option[ErrorOrStatus])] =
    upload(podName, Path.fromNioPath(sourceFile), Path.fromNioPath(destinationFile), container)

  def upload(
      podName: String,
      sourceFile: Path,
      destinationFile: Path,
      container: Option[String] = None
  ): F[(List[StdErr], Option[ErrorOrStatus])] = {
    val mkDirResult = destinationFile.parent match {
      case Some(dir) =>
        execRequest(
          podName,
          Seq("sh", "-c", s"mkdir -p $dir"),
          container
        ).flatMap { mkDirRequest =>
          wsClient
            .connectHighLevel(mkDirRequest)
            .map(conn => F.delay(conn.receiveStream.through(processWebSocketData)))
            .use(_.flatMap(foldErrorStream))
        }
      case None =>
        (List.empty -> None).pure
    }

    val uploadRequest = execRequest(
      podName,
      Seq("sh", "-c", s"cat - >$destinationFile"),
      container,
      stdin = true
    )

    val uploadFileResult =
      uploadRequest.flatMap { uploadRequest =>
        wsClient.connectHighLevel(uploadRequest).use { connection =>
          val source   = Files[F].readAll(sourceFile, 4096, Flags.Read)
          val sendData = source
            .mapChunks(chunk => Chunk(WSFrame.Binary(ByteVector(chunk.toChain.prepend(StdInId).toVector))))
            .through(connection.sendPipe)
          val retryAttempts = 5
          val sendWithRetry = Stream
            .retry(sendData.compile.drain, delay = 500.millis, nextDelay = _ * 2, maxAttempts = retryAttempts)
            .onError { case e =>
              Stream.eval(Logger[F].error(e)(s"Failed send file data after $retryAttempts attempts"))
            }

          val result = for {
            signal <- Stream.eval(SignallingRef[F, Boolean](false))
            dataStream = sendWithRetry *> Stream.eval(signal.set(true))

            output = connection.receiveStream
              .through(
                processWebSocketData
              )
              .interruptWhen(signal)
              .concurrently(dataStream)

            errors = foldErrorStream(
              output
            ).map { case (errors, _) => errors }
          } yield errors

          result.compile.lastOrError.flatten
        }
      }

    for {
      result <- mkDirResult
      (errors, status) = result
      uploadErrors <- uploadFileResult
    } yield (errors ++ uploadErrors, status)
  }

  def execStream(
      podName: String,
      container: Option[String] = None,
      command: Seq[String] = Seq.empty,
      stdin: Boolean = false,
      stdout: Boolean = true,
      stderr: Boolean = true,
      tty: Boolean = false
  ): Resource[F, F[Stream[F, Either[ExecStream, ErrorOrStatus]]]] =
    Resource.eval(execRequest(podName, command, container, stdin, stdout, stderr, tty)).flatMap { request =>
      wsClient.connectHighLevel(request).map { connection =>
        F.delay(connection.receiveStream.through(processWebSocketData))
      }
    }

  def exec(
      podName: String,
      container: Option[String] = None,
      command: Seq[String] = Seq.empty,
      stdin: Boolean = false,
      stdout: Boolean = true,
      stderr: Boolean = true,
      tty: Boolean = false
  ): F[(List[ExecStream], Option[ErrorOrStatus])] =
    execStream(podName, container, command, stdin, stdout, stderr, tty).use(_.flatMap(foldStream))

  private def foldStream(
      stdoutStream: Stream[F, Either[ExecStream, ErrorOrStatus]]
  ) =
    stdoutStream.compile.fold((List.empty[ExecStream], none[ErrorOrStatus])) { case ((accEvents, accStatus), data) =>
      data match {
        case Left(event) =>
          (accEvents :+ event) -> accStatus
        case Right(errorOrStatus) =>
          accEvents -> accStatus.orElse(errorOrStatus.some)
      }
    }

  private def foldErrorStream(
      stdoutStream: Stream[F, Either[ExecStream, ErrorOrStatus]]
  ) =
    stdoutStream.compile.fold((List.empty[StdErr], none[ErrorOrStatus])) { case ((accEvents, accStatus), data) =>
      data match {
        case Left(event) =>
          accEvents ++ (event match {
            case e: StdErr => e.some
            case _         => none[StdErr]
          }).toList -> accStatus
        case Right(errorOrStatus) =>
          accEvents -> accStatus.orElse(errorOrStatus.some)
      }
    }

  private def processWebSocketData: Pipe[F, WSDataFrame, Either[ExecStream, ErrorOrStatus]] =
    _.collect {
      case WSFrame.Binary(data, _) if data.headOption.contains(StdOutId) =>
        StdOut(dropRoutingByte(data).toArray).asLeft

      case WSFrame.Binary(data, _) if data.headOption.contains(StdErrId) =>
        StdErr(dropRoutingByte(data).toArray).asLeft

      case WSFrame.Binary(data, _) if data.headOption.contains(StatusId) =>
        val json = convertToString(dropRoutingByte(data))
        decode[Status](json).leftFlatMap(_ => ParseFailure(json).asLeft[Status]).asRight[ExecStream]
    }

  private def dropRoutingByte(data: ByteVector) =
    data.drop(1)

  private def convertToString(data: ByteVector) =
    new String(data.toArray, Charset.`UTF-8`.nioCharset)

  /** @param podName
    *   The pod for which to stream logs.
    * @param container
    *   The container for which to stream logs. Defaults to only container if there is one container in the pod.
    * @param follow
    *   Follow the log stream of the pod. Defaults to false.
    * @param insecureSkipTLSVerifyBackend
    *   indicates that the apiserver should not confirm the validity of the serving certificate of the backend it is
    *   connecting to. This will make the HTTPS connection between the apiserver and the backend insecure. This means
    *   the apiserver cannot verify the log data it is receiving came from the real kubelet. If the kubelet is
    *   configured to verify the apiserver's TLS credentials, it does not mean the connection to the real kubelet is
    *   vulnerable to a man in the middle attack (e.g. an attacker could not intercept the actual log data coming from
    *   the real kubelet).
    * @param limitBytes
    *   If set, the number of bytes to read from the server before terminating the log output. This may not display a
    *   complete final line of logging, and may return slightly more or slightly less than the specified limit.
    * @param pretty
    *   If 'true', then the output is pretty printed. Defaults to false.
    * @param previous
    *   Return previous terminated container logs. Defaults to false.
    * @param sinceSeconds
    *   A relative time in seconds before the current time from which to show logs. If this value precedes the time a
    *   pod was started, only logs since the pod start will be returned. If this value is in the future, no logs will be
    *   returned. Only one of sinceSeconds or sinceTime may be specified.
    * @param tailLines
    *   If set, the number of lines from the end of the logs to show. If not specified, logs are shown from the creation
    *   of the container or sinceSeconds or sinceTime
    * @param timestamps
    *   If true, add an RFC3339 or RFC3339Nano timestamp at the beginning of every line of log output. Defaults to
    *   false.
    * @return
    */
  private def logRequest(
      podName: String,
      container: Option[String],
      follow: Boolean,
      insecureSkipTLSVerifyBackend: Boolean,
      limitBytes: Option[Long],
      pretty: Boolean,
      previous: Boolean,
      sinceSeconds: Option[Long],
      tailLines: Option[Long],
      timestamps: Boolean
  ): F[Request[F]] = {
    val uri = (config.server.resolve(resourceUri) / podName / "log")
      .+??("container" -> container)
      .+?("follow" -> follow.toString)
      .+?("insecureSkipTLSVerifyBackend" -> insecureSkipTLSVerifyBackend.toString)
      .+??("limitBytes" -> limitBytes.map(_.toString))
      .+?("pretty" -> pretty.toString)
      .+?("previous" -> previous.toString)
      .+??("sinceSeconds" -> sinceSeconds.map(_.toString))
      .+??("tailLines" -> tailLines.map(_.toString))
      .+?("timestamps" -> timestamps.toString)

    Request[F](method = Method.GET, uri = uri).withOptionalAuthorization(authorization)
  }

  def log(
      podName: String,
      container: Option[String] = None,
      follow: Boolean = false,
      insecureSkipTLSVerifyBackend: Boolean = false,
      limitBytes: Option[Long] = None,
      pretty: Boolean = false,
      previous: Boolean = false,
      sinceSeconds: Option[Long] = None,
      tailLines: Option[Long] = None,
      timestamps: Boolean = false
  ): Stream[F, Response[F]] =
    fs2.Stream
      .eval(
        logRequest(
          podName,
          container,
          follow,
          insecureSkipTLSVerifyBackend,
          limitBytes,
          pretty,
          previous,
          sinceSeconds,
          tailLines,
          timestamps
        )
      )
      .flatMap { request =>
        httpClient.stream(request)
      }

}
