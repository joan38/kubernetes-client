package com.goyeau.kubernetes.client.operation

import cats.{Applicative, Parallel}
import cats.effect.concurrent.Ref
import cats.implicits._
import com.goyeau.kubernetes.client.{EventType, KubernetesClient, WatchEvent}
import fs2.{Pipe, Stream}
import fs2.concurrent.SignallingRef
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s.Status
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable
import scala.concurrent.duration._
import scala.language.reflectiveCalls

trait WatchableTests[F[_], Resource <: { def metadata: Option[ObjectMeta] }]
    extends AnyFlatSpec
    with Matchers
    with OptionValues
    with MinikubeClientProvider[F] {
  implicit def parallel: Parallel[F]

  def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[F]): Creatable[F, Resource]

  def sampleResource(resourceName: String): Resource

  def getChecked(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]): F[Resource]

  def modifyResource(resource: Resource): Resource

  def deleteApi(namespaceName: String)(implicit client: KubernetesClient[F]): Deletable[F]

  def watchApi(namespaceName: String)(implicit client: KubernetesClient[F]): Watchable[F, Resource]

  "watch" should s"watch a $resourceName events" in usingMinikube { implicit client =>
    val namespaceName = s"$resourceName".toLowerCase
    val name          = resourceName.toLowerCase

    val sendEvents = for {
      status <- namespacedApi(namespaceName).create(sampleResource(name))
      _ = status shouldBe Status.Created
      resource <- getChecked(namespaceName, name)
      status   <- namespacedApi(namespaceName).createOrUpdate(modifyResource(resource))
      _ = status shouldBe Status.Ok
      status <- deleteApi(namespaceName).delete(name)
      _ = status shouldBe Status.Ok
    } yield ()

    val expected = Set[EventType](EventType.ADDED, EventType.MODIFIED, EventType.DELETED)

    def processEvent(
        received: Ref[F, mutable.Set[EventType]],
        signal: SignallingRef[F, Boolean]
    ): Pipe[F, Either[String, WatchEvent[Resource]], Unit] =
      _.flatMap {
        case Right(we) if we.`object`.metadata.exists(_.name.exists(_ == name)) =>
          Stream.eval {
            for {
              _           <- received.update(_ += we.`type`)
              allReceived <- received.get.map(_.intersect(expected) == expected)
              _           <- F.whenA(allReceived)(signal.set(true))
            } yield ()
          }
        case _ => Stream.eval(Applicative[F].unit)
      }

    val watchStream = for {
      signal   <- Stream.eval(SignallingRef[F, Boolean](false))
      received <- Stream.eval(Ref.of(mutable.Set.empty[EventType]))
      watch <- watchApi(namespaceName).watch
        .through(processEvent(received, signal))
        .evalMap(_ => received.get)
        .interruptWhen(signal)
    } yield watch

    val watchEvents = for {
      set <- watchStream.interruptAfter(60.seconds).compile.toList
      result = set.headOption.getOrElse(fail("stream should have at least one element with all received events"))
      _      = result should === (expected)
    } yield ()

    (
      watchEvents,
      timer.sleep(100.millis) *> sendEvents
    ).parSequence
  }
}
