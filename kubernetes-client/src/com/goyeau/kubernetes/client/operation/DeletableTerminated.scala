package com.goyeau.kubernetes.client.operation

import scala.concurrent.duration._
import cats.Applicative
import cats.effect.Timer
import cats.implicits._
import io.k8s.apimachinery.pkg.apis.meta.v1.DeleteOptions
import org.http4s._

private[client] trait DeletableTerminated[F[_]] { this: Deletable[F] =>

  def deleteTerminated(name: String, deleteOptions: Option[DeleteOptions] = None)(implicit
      timer: Timer[F]
  ): F[Status] = {
    def deleteTerminated(firstTry: Boolean): F[Status] = {
      def retry() = timer.sleep(1.second) *> deleteTerminated(firstTry = false)

      delete(name, deleteOptions).flatMap {
        case status if status.isSuccess => retry()
        case Status.Conflict            => retry()
        case response @ Status.NotFound =>
          if (firstTry) Applicative[F].pure(response) else Applicative[F].pure(Status.Ok)
        case error => Applicative[F].pure(error)
      }
    }

    deleteTerminated(firstTry = true)
  }
}
