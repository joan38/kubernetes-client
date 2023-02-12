package com.goyeau.kubernetes.client.operation

import cats.Parallel
import cats.effect.Ref
import cats.implicits.*
import com.goyeau.kubernetes.client.Utils.retry
import com.goyeau.kubernetes.client.api.CustomResourceDefinitionsApiTest
import com.goyeau.kubernetes.client.{EventType, KubernetesClient, WatchEvent}
import fs2.concurrent.SignallingRef
import fs2.{Pipe, Stream}
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import munit.FunSuite
import org.http4s.Status

import scala.concurrent.duration.*
import scala.language.reflectiveCalls

trait WatchableTests[F[_], Resource <: { def metadata: Option[ObjectMeta] }]
    extends FunSuite
    with MinikubeClientProvider[F] {
  implicit def parallel: Parallel[F]

  val watchIsNamespaced = true

  override protected val extraNamespace = List("anothernamespace-" + defaultNamespace)

  def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[F]): Creatable[F, Resource]

  def sampleResource(resourceName: String, labels: Map[String, String]): Resource

  def getChecked(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]): F[Resource]

  def modifyResource(resource: Resource): Resource

  def deleteApi(namespaceName: String)(implicit client: KubernetesClient[F]): Deletable[F]

  def watchApi(namespaceName: String)(implicit client: KubernetesClient[F]): Watchable[F, Resource]

  def api(implicit client: KubernetesClient[F]): Watchable[F, Resource]

  def deleteResource(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]): F[Status] =
    deleteApi(namespaceName).delete(resourceName)

  private def update(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[F]) =
    for {
      resource <- getChecked(namespaceName, resourceName)
      status   <- createOrUpdate(namespaceName, resource)
      _ = assertEquals(status, Status.Ok)
    } yield ()

  private def createOrUpdate(namespaceName: String, resource: Resource)(implicit
      client: KubernetesClient[F]
  ): F[Status] =
    namespacedApi(namespaceName).createOrUpdate(modifyResource(resource))

  private def sendEvents(namespace: String, resourceName: String)(implicit client: KubernetesClient[F]) =
    for {
      _ <- retry(
        create(namespace, resourceName),
        maxRetries = 30,
        actionClue = Some(s"Creating $resourceName in $namespace ns")
      )
      _      <- retry(update(namespace, resourceName))
      status <- deleteResource(namespace, resourceName)
      _ = assertEquals(status, Status.Ok, status.sanitizedReason)
    } yield ()

  private def create(namespace: String, resourceName: String)(implicit client: KubernetesClient[F]) =
    for {
      ns <- client.namespaces.get(namespace)
      _ <- logger.info(
        s"creating in namespace: ${ns.metadata.flatMap(_.name).getOrElse("n/a/")}, status: ${ns.status.flatMap(_.phase)}"
      )
      status <- namespacedApi(namespace).create(sampleResource(resourceName, Map.empty))
      _ = assertEquals(status, Status.Created, status.sanitizedReason)
    } yield ()

  private def watchEvents(
      expected: Map[String, Set[EventType]],
      resourceName: String,
      watchingNamespace: Option[String]
  )(implicit
      client: KubernetesClient[F]
  ) = {
    def isExpectedResource(we: WatchEvent[Resource]): Boolean =
      we.`object`.metadata.exists(_.name.exists { name =>
        name == resourceName || name == CustomResourceDefinitionsApiTest.crdName(resourceName)
      })
    def processEvent(
        received: Ref[F, Map[String, Set[EventType]]],
        signal: SignallingRef[F, Boolean]
    ): Pipe[F, Either[String, WatchEvent[Resource]], Unit] =
      _.flatMap {
        case Right(we) if isExpectedResource(we) =>
          Stream.eval {
            for {
              _ <- received.update(events =>
                we.`object`.metadata.flatMap(_.namespace) match {
                  case Some(namespace) =>
                    val updated = events.get(namespace) match {
                      case Some(namespaceEvents) => namespaceEvents + we.`type`
                      case _                     => Set(we.`type`)
                    }
                    events.updated(namespace, updated)
                  case _ =>
                    val crdNamespace = "customresourcedefinition"
                    events.updated(crdNamespace, events.getOrElse(crdNamespace, Set.empty) + we.`type`)
                }
              )
              allReceived <- received.get.map(_ == expected)
              _           <- F.whenA(allReceived)(signal.set(true))
            } yield ()
          }
        case _ => Stream.eval(F.unit)
      }

    val watchEvents = for {
      signal         <- SignallingRef[F, Boolean](false)
      receivedEvents <- Ref.of(Map.empty[String, Set[EventType]])
      watchStream = watchingNamespace
        .map(watchApi)
        .getOrElse(api)
        .watch()
        .through(processEvent(receivedEvents, signal))
        .evalMap(_ => receivedEvents.get)
        .interruptWhen(signal)
      _      <- watchStream.interruptAfter(60.seconds).compile.drain
      events <- receivedEvents.get
    } yield events

    for {
      result <- watchEvents
      _ = assertEquals(result, expected)
    } yield ()
  }

  private def sendToAnotherNamespace(name: String)(implicit client: KubernetesClient[F]) =
    F.whenA(watchIsNamespaced)(
      extraNamespace.map(sendEvents(_, name)).sequence
    )

  test(s"watch $resourceName events in all namespaces") {
    usingMinikube { implicit client =>
      val name           = s"${resourceName.toLowerCase}-watch-all"
      val expectedEvents = Set[EventType](EventType.ADDED, EventType.MODIFIED, EventType.DELETED)
      val expected =
        if (watchIsNamespaced)
          (defaultNamespace +: extraNamespace.toList).map(_ -> expectedEvents).toMap
        else
          Map(defaultNamespace -> expectedEvents)

      (
        watchEvents(expected, name, None),
        F.sleep(100.millis) *> sendEvents(defaultNamespace, name) *> sendToAnotherNamespace(name)
      ).parTupled
    }
  }

  test(s"watch $resourceName events in the single namespace") {
    usingMinikube { implicit client =>
      assume(watchIsNamespaced)
      val name     = s"${resourceName.toLowerCase}-watch-single"
      val expected = Set[EventType](EventType.ADDED, EventType.MODIFIED, EventType.DELETED)

      (
        watchEvents(Map(defaultNamespace -> expected), name, Some(defaultNamespace)),
        F.sleep(100.millis) *> sendEvents(defaultNamespace, name) *> sendToAnotherNamespace(name)
      ).parTupled
    }
  }
}
