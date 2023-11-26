package com.goyeau.kubernetes.client.operation

import com.goyeau.kubernetes.client.MinikubeClientProvider
import cats.effect.*
import cats.syntax.all.*
import com.goyeau.kubernetes.client.Utils.retry
import com.goyeau.kubernetes.client.api.CustomResourceDefinitionsApiTest
import com.goyeau.kubernetes.client.{EventType, KubernetesClient, WatchEvent}
import fs2.concurrent.SignallingRef
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import org.http4s.Status

import scala.concurrent.duration.*
import scala.language.reflectiveCalls
import org.http4s.client.UnexpectedStatus

trait WatchableTests[R <: { def metadata: Option[ObjectMeta] }] {
  self: MinikubeClientProvider =>

  val watchIsNamespaced = true

  override protected val extraNamespace = List("anothernamespace-" + defaultNamespace)

  def namespacedApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Creatable[IO, R]

  def sampleResource(resourceName: String, labels: Map[String, String]): R

  def getChecked(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[IO]): IO[R]

  def modifyResource(resource: R): R

  def deleteApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Deletable[IO]

  def watchApi(namespaceName: String)(implicit client: KubernetesClient[IO]): Watchable[IO, R]

  def api(implicit client: KubernetesClient[IO]): Watchable[IO, R]

  def deleteResource(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[IO]): IO[Status] =
    deleteApi(namespaceName).delete(resourceName)

  private def update(namespaceName: String, resourceName: String)(implicit client: KubernetesClient[IO]) =
    for {
      resource <- getChecked(namespaceName, resourceName)
      status   <- createOrUpdate(namespaceName, modifyResource(resource))
      _ = assertEquals(status, Status.Ok)
    } yield ()

  private def createOrUpdate(namespaceName: String, resource: R)(implicit
      client: KubernetesClient[IO]
  ): IO[Status] =
    namespacedApi(namespaceName).createOrUpdate(resource)

  private def sendEvents(namespace: String, resourceName: String)(implicit client: KubernetesClient[IO]) =
    for {
      _ <- retry(
        createIfMissing(namespace, resourceName),
        maxRetries = 30,
        actionClue = Some(s"Creating $resourceName in $namespace ns")
      )
      _      <- retry(update(namespace, resourceName), actionClue = Some(s"Updating $resourceName"))
      status <- deleteResource(namespace, resourceName)
      _ = assertEquals(status, Status.Ok, status.sanitizedReason)
    } yield ()

  private def createIfMissing(namespace: String, resourceName: String)(implicit client: KubernetesClient[IO]) =
    getChecked(namespace, resourceName).as(()).recoverWith {
      case err: UnexpectedStatus if err.status == Status.NotFound =>
        for {
          ns <- client.namespaces.get(namespace)
          _ <- logger.info(
            s"creating in namespace: ${ns.metadata.flatMap(_.name).getOrElse("n/a/")}, status: ${ns.status.flatMap(_.phase)}"
          )
          status <- namespacedApi(namespace).create(sampleResource(resourceName, Map.empty))
          _ = assertEquals(status.isSuccess, true, s"${status.sanitizedReason} should be success")
        } yield ()
    }

  private def watchEvents(
      expected: Map[String, Set[EventType]],
      resourceName: String,
      watchingNamespace: Option[String],
      resourceVersion: Option[String] = None
  )(implicit
      client: KubernetesClient[IO]
  ) = {
    def isExpectedResource(we: WatchEvent[R]): Boolean =
      we.`object`.metadata.exists(_.name.exists { name =>
        name == resourceName || name == CustomResourceDefinitionsApiTest.crdName(resourceName)
      })
    def processEvent(
        received: Ref[IO, Map[String, Set[EventType]]],
        signal: SignallingRef[IO, Boolean],
        event: Either[String, WatchEvent[R]]
    ): IO[Unit] =
      event match {
        case Right(we) if isExpectedResource(we) =>
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
            _           <- IO.whenA(allReceived)(signal.set(true))
          } yield ()
        case _ => IO.unit
      }

    val watchEvents = for {
      signal         <- SignallingRef[IO, Boolean](false)
      receivedEvents <- IO.ref(Map.empty[String, Set[EventType]])
      watchStream = watchingNamespace
        .fold(api)(watchApi)
        .watch(resourceVersion = resourceVersion)
        .evalTap(processEvent(receivedEvents, signal, _))
        .interruptWhen(signal)
      _      <- watchStream.interruptAfter(60.seconds).compile.drain
      events <- receivedEvents.get
    } yield events

    for {
      result <- watchEvents
      _ = assertEquals(result, expected)
    } yield ()
  }

  private def sendToAnotherNamespace(name: String)(implicit client: KubernetesClient[IO]) =
    IO.whenA(watchIsNamespaced)(
      extraNamespace.traverse_(sendEvents(_, name))
    )

  test(s"watch $resourceName events in all namespaces") {
    usingMinikube { implicit client =>
      val name           = s"${resourceName.toLowerCase}-watch-all"
      val expectedEvents = Set[EventType](EventType.ADDED, EventType.MODIFIED, EventType.DELETED)
      val expected =
        if (watchIsNamespaced)
          (defaultNamespace +: extraNamespace).map(_ -> expectedEvents).toMap
        else
          Map(defaultNamespace -> expectedEvents)

      (
        watchEvents(expected, name, None),
        IO.sleep(100.millis) *> sendEvents(defaultNamespace, name) *> sendToAnotherNamespace(name)
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
        IO.sleep(100.millis) *> sendEvents(defaultNamespace, name) *> sendToAnotherNamespace(name)
      ).parTupled
    }
  }

  test(s"watch $resourceName events from a given resourceVersion") {
    usingMinikube { implicit client =>
      val name     = s"${resourceName.toLowerCase}-watch-resource-version"
      val expected = Set[EventType](EventType.MODIFIED, EventType.DELETED)

      for {
        _ <- retry(
          createIfMissing(defaultNamespace, name),
          actionClue = Some(s"createIfMissing $defaultNamespace/$name")
        )
        resource <- getChecked(defaultNamespace, name)
        resourceVersion = resource.metadata.flatMap(_.resourceVersion).get
        _ <- (
          watchEvents(Map(defaultNamespace -> expected), name, Some(defaultNamespace), Some(resourceVersion)),
          IO.sleep(100.millis) *> sendEvents(defaultNamespace, name) *> sendToAnotherNamespace(name)
        ).parTupled
      } yield ()
    }
  }
}
