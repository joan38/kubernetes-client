package com.goyeau.kubernetesclient

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
  val strategicMergePatch = ContentType(
    MediaType.customWithFixedCharset("application", "strategic-merge-patch+json", HttpCharsets.`UTF-8`)
  )
  val mergePatch = ContentType(
    MediaType.customWithFixedCharset("application", "merge-patch+json", HttpCharsets.`UTF-8`)
  )
  val jsonPatch = ContentType(MediaType.customWithFixedCharset("application", "json-patch+json", HttpCharsets.`UTF-8`))

  def singleRequest[Data: Encoder](config: KubeConfig,
                                   method: HttpMethod,
                                   uri: Uri,
                                   data: Option[Data] = None,
                                   contentType: ContentType = ContentTypes.`application/json`)(
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
            HttpEntity(contentType, ByteString(printer.pretty(data.asJson)))
          }
        ),
        SecurityUtils.httpsConnectionContext(config)
      )
      entity <- response.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
      _ = if (response.status.isFailure) throw KubernetesException(response.status.intValue, entity.utf8String)
    } yield entity.utf8String
  }
}

case class KubernetesException(code: Int, message: String) extends Exception(message)
