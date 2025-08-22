package com.goyeau.kubernetes.client.operation

import cats.effect.*
import cats.implicits.*
import com.goyeau.kubernetes.client.Utils.retry
import com.goyeau.kubernetes.client.api.NamespacesApiTest
import com.goyeau.kubernetes.client.{KubeConfig, KubernetesClient}
import fs2.io.file.Path
import munit.Suite
import org.typelevel.log4cats.Logger
import io.k8s.apimachinery.pkg.apis.meta.v1.DeleteOptions
import fs2.io.file.Files
import org.http4s.ember.client.EmberClientBuilder
import com.goyeau.kubernetes.client.util.SslContexts
import org.http4s.client.middleware.{Logger => ClientLogger}

trait MinikubeClientProvider[F[_]] {
  this: Suite =>

  implicit def F: Async[F]
  implicit def G: Files[F]
  implicit def logger: Logger[F]

  def unsafeRunSync[A](f: F[A]): A
  
  val kubeConfig = KubeConfig.fromFile[F](
    Path(s"${System.getProperty("user.home")}/.kube/config"),
    sys.env.getOrElse("KUBE_CONTEXT_NAME", "minikube")
  )
  
  val emberBasedKubernetesClient: Resource[F, KubernetesClient[F]] = {
    for {
      kc <- Resource.eval(kubeConfig)
      tlsContext = SslContexts.tlsFromConfig(kc)
      emberClient <- EmberClientBuilder.default.withTLSContext(tlsContext).build.map(c => ClientLogger(false, false)(c))
      k8Client <- KubernetesClient(kc, Some(emberClient))
    } yield k8Client
  }

  val kubernetesClient: Resource[F, KubernetesClient[F]] = {
    KubernetesClient(kubeConfig)
  }

  def resourceName: String

  def defaultNamespace: String = resourceName.toLowerCase

  protected val extraNamespace = List.empty[String]

  protected def createNamespace(namespace: String): F[Unit] = kubernetesClient.use { implicit client =>
    client.namespaces.deleteTerminated(namespace) *> retry(
      NamespacesApiTest.createChecked[F](namespace),
      actionClue = Some(s"Creating '$namespace' namespace")
    )
  }.void

  private def deleteNamespace(namespace: String) = kubernetesClient.use { client =>
    client.namespaces.delete(
      namespace,
      DeleteOptions(gracePeriodSeconds = 0L.some, propagationPolicy = "Foreground".some).some
    )
  }.void

  protected def createNamespaces(): Unit = {
    val ns = defaultNamespace +: extraNamespace
    unsafeRunSync(
      logger.info(s"Creating namespaces: $ns") *>
        ns.traverse_(name => createNamespace(name))
    )
  }

  override def beforeAll(): Unit =
    createNamespaces()

  override def afterAll(): Unit = {
    val ns = defaultNamespace +: extraNamespace
    unsafeRunSync(
      logger.info(s"Deleting namespaces: $ns") *>
        ns.traverse_(name => deleteNamespace(name))
    )
  }

  def usingMinikube[T](body: KubernetesClient[F] => F[T]): T =
    unsafeRunSync(kubernetesClient.use(body))
}
