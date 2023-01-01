package com.goyeau.kubernetes.client.api

import cats.effect.{Async, Resource}
import cats.syntax.all.*
import com.goyeau.kubernetes.client.KubeConfig
import com.goyeau.kubernetes.client.api.ExecRouting.*
import com.goyeau.kubernetes.client.api.ExecStream.{StdErr, StdOut}
import com.goyeau.kubernetes.client.api.NamespacedPodsApi.ErrorOrStatus
import com.goyeau.kubernetes.client.operation.*
import com.goyeau.kubernetes.client.util.CachedExecToken
import fs2.concurrent.SignallingRef
import fs2.io.file.{Files, Flags, Path}
import fs2.{Chunk, Pipe, Stream}
import io.circe.*
import io.circe.parser.decode
import io.k8s.api.core.v1.{Pod, PodList}
import io.k8s.apimachinery.pkg.apis.meta.v1.Status
import org.http4s.*
import org.http4s.client.Client
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
    val cachedExecToken: Option[CachedExecToken[F]]
)(implicit
    val F: Async[F],
    val listDecoder: Decoder[PodList],
    val resourceDecoder: Decoder[Pod],
    encoder: Encoder[Pod]
) extends Listable[F, PodList]
    with Watchable[F, Pod] {
  val resourceUri: Uri = uri"/api" / "v1" / "pods"

  def namespace(namespace: String): NamespacedPodsApi[F] =
    new NamespacedPodsApi(httpClient, wsClient, config, cachedExecToken, namespace)
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
    val cachedExecToken: Option[CachedExecToken[F]],
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

  private val execHeaders: F[Headers] =
    config.authorization
      .map { auth =>
        auth.map(auth => Headers(auth))
      }
      .getOrElse(Headers.empty.pure[F])
      .map(_.put(Header.Raw(CIString("Sec-WebSocket-Protocol"), "v4.channel.k8s.io")))

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
    val uri = (webSocketAddress.resolve(resourceUri) / podName / "exec")
      .+?("stdin" -> stdin.toString)
      .+?("stdout" -> stdout.toString)
      .+?("stderr" -> stderr.toString)
      .+?("tty" -> tty.toString)
      .+??("container" -> container)
      .++?("command" -> commands)

    execHeaders.map { execHeaders =>
      WSRequest(uri, execHeaders, Method.POST)
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
          val source = Files[F].readAll(sourceFile, 4096, Flags.Read)
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
}
