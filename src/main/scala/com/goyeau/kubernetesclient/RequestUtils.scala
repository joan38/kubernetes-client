package com.goyeau.kubernetesclient

import java.io.IOException

import scala.concurrent.{ExecutionContext, Future}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.stream.ActorMaterializer
import akka.util.ByteString
import io.circe._
import io.circe.syntax._

object RequestUtils {
  implicit val nothingEncoder: Encoder[Nothing] = null

  def singleRequest[Data: Encoder](config: KubeConfig, method: HttpMethod, uri: Uri, data: Option[Data] = None)(
    implicit ec: ExecutionContext,
    system: ActorSystem,
  ): Future[String] = {
    implicit val materializer: ActorMaterializer = ActorMaterializer()
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
        throw exception
      }
    } yield entity.utf8String
  }
}
