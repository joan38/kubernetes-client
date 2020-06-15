# Kubernetes Client for Scala

[![Latest version](https://index.scala-lang.org/joan38/kubernetes-client/kubernetes-client/latest.svg?color=blue)](https://index.scala-lang.org/joan38/kubernetes-client/kubernetes-client)

A pure functional client for Kubernetes.

## Installation
[Mill](https://www.lihaoyi.com/mill):
```scala
ivy"com.goyeau::kubernetes-client:<latest version>"
```
or

[SBT](https://www.scala-sbt.org):
```scala
"com.goyeau" %% "kubernetes-client" % "<latest version>"
```

## Usage

### Client configuration example
```scala
import cats.effect.{ContextShift, IO, Timer}
import com.goyeau.kubernetes.client._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import java.io.File
import org.http4s.AuthScheme
import org.http4s.Credentials.Token
import org.http4s.headers.Authorization
import org.http4s.implicits._
import scala.concurrent.ExecutionContext
import scala.io.Source

implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

val kubernetesClient =
  KubernetesClient[IO](
    KubeConfig(
      server = uri"https://k8s.goyeau.com",
      authorization = Option(Authorization(Token(AuthScheme.Bearer, Source.fromFile("/var/run/secrets/kubernetes.io/serviceaccount/token").mkString))),
      caCertFile = Option(new File("/var/run/secrets/kubernetes.io/serviceaccount/ca.crt"))
    )
  )
```

```scala
import cats.effect.{ContextShift, IO, Timer}
import com.goyeau.kubernetes.client._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import java.io.File
import scala.concurrent.ExecutionContext

implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

val kubernetesClient =
  KubernetesClient[IO](KubeConfig.fromFile[IO](new File(s"${System.getProperty("user.home")}/.kube/config")))
```

### Requests

```scala
import cats.effect.{ContextShift, IO, Timer}
import com.goyeau.kubernetes.client._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.k8s.api.apps.v1._
import io.k8s.api.core.v1._
import io.k8s.apimachinery.pkg.api.resource.Quantity
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import java.io.File
import scala.concurrent.ExecutionContext

implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)
implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

val kubernetesClient =
  KubernetesClient(KubeConfig.fromFile[IO](new File(s"${System.getProperty("user.home")}/.kube/config")))

val deployment = Deployment(
  metadata = Option(ObjectMeta(name = Option("web-backend"), namespace = Option("my-namespace"))),
  spec = Option(
    DeploymentSpec(
      selector = null,
      strategy = Option(
        DeploymentStrategy(
          `type` = Option("RollingUpdate"),
          rollingUpdate = Option(RollingUpdateDeployment(Option(StringValue("10%")), Option(StringValue("50%"))))
        )
      ),
      template = PodTemplateSpec(
        metadata = Option(
          ObjectMeta(
            labels = Option(Map("app" -> "web", "tier" -> "frontend", "environment" -> "myenv"))
          )
        ),
        spec = Option(
          PodSpec(
            containers = Seq(
              Container(
                name = "nginx",
                image = Option("nginx"),
                resources = Option(
                  ResourceRequirements(
                    Option(Map("cpu" -> Quantity("100m"), "memory" -> Quantity("128Mi"))),
                    Option(Map("cpu" -> Quantity("80m"), "memory" -> Quantity("64Mi")))
                  )
                ),
                volumeMounts = Option(Seq(VolumeMount(name = "nginx-config", mountPath = "/etc/nginx/conf.d"))),
                ports = Option(Seq(ContainerPort(name = Option("http"), containerPort = 8080)))
              )
            ),
            volumes = Option(
              Seq(
                Volume(
                  name = "nginx-config",
                  configMap = Option(ConfigMapVolumeSource(name = Option("nginx-config")))
                )
              )
            )
          )
        )
      )
    )
  )
)

kubernetesClient.use { client =>
  client.deployments.namespace("my-namespace").create(deployment)
}
```


## Related projects

* [Skuber](https://github.com/doriordan/skuber)
* [Kubernetes Client for Java](https://github.com/kubernetes-client/java)


## Why Kubernetes Client for Scala?

You might wonder why using this library instead of Skuber for example? Kubernetes Client is a pure functional based on
Cats and Http4s.  
Another benefit of Kubernetes Client is that (like the [Kubernetes Client for Java](https://github.com/kubernetes-client/java/#update-the-generated-code))
it is generating all the payload case classes by just ingesting the swagger api provided by Kubernetes' main repo. That
means this project will always remain up to date with the latest Kubernetes API.
