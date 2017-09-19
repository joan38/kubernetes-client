package com.goyeau.kubernetesclient

import java.io.IOException

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
            HttpEntity(ContentTypes.`application/json`, ByteString(data.asJson.noSpaces))
          }
        ),
        SecurityUtils.httpsConnectionContext(config)
      )
      entity <- response.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
      _ = if (response.status.isFailure) {
        val message = s"${response.status.reason}: ${entity.utf8String}"
        logger.error(message)
        throw new IOException(message)
      }
    } yield entity.utf8String
}
