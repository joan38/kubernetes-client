package com.goyeau.kubernetes.client.operation

import cats.syntax.all.*
import scala.concurrent.duration.*
import cats.effect.Temporal
import io.k8s.apimachinery.pkg.apis.meta.v1.DeleteOptions
import org.http4s.*

private[client] trait DeletableTerminated[F[_]] { this: Deletable[F] =>

  def deleteTerminated(name: String, deleteOptions: Option[DeleteOptions] = None)(implicit
      temporal: Temporal[F]
  ): F[Status] = {

    def deleteTerminated(firstTry: Boolean): F[Status] = {

      def retry() =
        temporal.sleep(1.second) *> deleteTerminated(firstTry = false)

      delete(name, deleteOptions).flatMap {
        case status if status.isSuccess => retry()
        case Status.Conflict            => retry()
        case response @ Status.NotFound =>
          if (firstTry) F.pure(response) else F.pure(Status.Ok)
        case error => F.pure(error)
      }
    }

    deleteTerminated(firstTry = true)
  }
}
