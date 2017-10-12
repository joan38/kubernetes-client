package com.goyeau.kubernetesclient

import java.io.{IOException, PrintWriter, StringWriter}

import scala.concurrent.{ExecutionContext, Future}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.stream.Materializer
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import io.circe._
import io.circe.syntax._

object RequestUtils extends LazyLogging {
  implicit val nothingEncoder: Encoder[Nothing] = null

  def singleRequest[Data: Encoder](config: KubeConfig, method: HttpMethod, uri: Uri, data: Option[Data] = None)(
    implicit ec: ExecutionContext,
    system: ActorSystem,
    mat: Materializer
  ): Future[String] =
    for {
      response <- Http().singleRequest(
        HttpRequest(
          method,
          uri,
          headers = config.oauthToken.toList.map(token => Authorization(OAuth2BearerToken(token))),
          entity = data.fold(HttpEntity.Empty) { data =>
            val printer = Printer.noSpaces.copy(dropNullKeys = true)
            HttpEntity(ContentTypes.`application/json`, ByteString(printer.pretty(data.asJson)))
          }
        ),
        SecurityUtils.httpsConnectionContext(config)
      )
      entity <- response.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
      _ = if (response.status.isFailure) {
        val exception = new IOException(s"${response.status.reason}: ${entity.utf8String}")
        val writer = new StringWriter()
        exception.printStackTrace(new PrintWriter(writer, true))
        logger.error(writer.toString)
        throw exception
      }
    } yield entity.utf8String
}
