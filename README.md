# Kubernetes Client for Scala

[![kubernetes-client Scala version support](https://index.scala-lang.org/joan38/kubernetes-client/kubernetes-client/latest-by-scala-version.svg)](https://index.scala-lang.org/joan38/kubernetes-client/kubernetes-client)

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

#### Standard configuration "chain"

```scala
import cats.effect.IO
import com.goyeau.kubernetes.client.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

val kubernetesClient =
  KubernetesClient[IO](
    KubeConfig.standard[IO]
  )
```

The `standard` configuration mimics the way `ClientBuilder.standard` from the official Java k8s client works:

* if KUBECONFIG env variable is set, and the file exists - it will be used; the 'current-context' specified in the file
  will be used
* otherwise, if ~/.kube/config file exists - it will be used; the 'current-context' specified in the file will be used
* otherwise, if cluster configuration is found - use it

Cluster configuration is defined by:

- `/var/run/secrets/kubernetes.io/serviceaccount/ca.crt` certificate file
- `/var/run/secrets/kubernetes.io/serviceaccount/token` token file
- `KUBERNETES_SERVICE_HOST` env variable (https protocol is assumed)
- `KUBERNETES_SERVICE_PORT` env variable

#### Manually providing the configuration

```scala
import cats.effect.IO
import com.goyeau.kubernetes.client.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import java.io.File
import org.http4s.AuthScheme
import org.http4s.Credentials.Token
import org.http4s.headers.Authorization
import org.http4s.implicits.*
import scala.concurrent.ExecutionContext
import scala.io.Source

implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

val kubernetesClient =
  KubernetesClient[IO](
    KubeConfig.of[IO](
      server = uri"https://k8s.goyeau.com",
      authorization = Option(Authorization(Token(AuthScheme.Bearer, Source.fromFile("/var/run/secrets/kubernetes.io/serviceaccount/token").mkString))),
      caCertFile = Option(new File("/var/run/secrets/kubernetes.io/serviceaccount/ca.crt"))
    )
  )
```

```scala
import cats.effect.IO
import com.goyeau.kubernetes.client.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import java.io.File
import scala.concurrent.ExecutionContext

implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

val kubernetesClient =
  KubernetesClient[IO](KubeConfig.fromFile[IO](new File(s"${System.getProperty("user.home")}/.kube/config")))
```

#### Authorization caching

It is possible (and recommended) to configure the kubernetes client to cache the authorization (and renew it, when/if it
expires).

```scala
import cats.effect.IO
import com.goyeau.kubernetes.client.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import scala.concurrent.duration._

implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

val kubernetesClient =
  KubernetesClient[IO](
    KubeConfig.standard[IO].withDefaultAuthorizationCache(5.minutes)
  )
```

When authorization cache is configured, the client attempts to derive the expiration time of the token:

* if it's a raw authorization header (provided directly, or from the token file inside the cluster), we attempt to
  decode it as a JWT and take the `exp` field from it;
* if authorization is provided by the auth plugin in the kube config file – the auth plugin provides the expiration
  alongside the token.

The cache works this way:

The first time the token is "requested" by the client, it will unconditionally delegate to the underlying
F[Authorization], and will cache the token.

* if the underlying F[Authorization] "throws", the cache throws as well.

When the token is requested subsequently:

* if the expiration time is not present (the token was not a JWT, the auth plugin did not specify expiration, etc) the
  cached authorization will be re-used forever
* if the expiration time is present, but it's far enough into the future (later than now +
  refreshTokenBeforeExpiration), the cached authorization will be re-used
* if the expiration time is present, and it's soon enough (sooner than now + refreshTokenBeforeExpiration), the
  underlying F[Authorization] will be evaluated
  * if it's successful, the new authorization is cached
  * if not, but the cached token is still valid, the cached token is re-used otherwise, it will raise an error.

If the cache is not configured (which is by default), the authorization will never be updated and might expire
eventually.

### Requests

```scala
import cats.effect.IO
import com.goyeau.kubernetes.client.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.k8s.api.apps.v1.*
import io.k8s.api.core.v1.*
import io.k8s.apimachinery.pkg.api.resource.Quantity
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import java.io.File
import scala.concurrent.ExecutionContext

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


## Development

### Pre-requisites

 - Java 11 or higher
 - Docker


## Related projects

* [Skuber](https://github.com/doriordan/skuber)
* [Kubernetes Client for Java](https://github.com/kubernetes-client/java)


## Why Kubernetes Client for Scala?

You might wonder why using this library instead of Skuber for example? Kubernetes Client is a pure functional based on
Cats and Http4s.  
Another benefit of Kubernetes Client is that (like the [Kubernetes Client for Java](https://github.com/kubernetes-client/java/#update-the-generated-code))
it is generating all the payload case classes by just ingesting the swagger api provided by Kubernetes' main repo. That
means this project will always remain up to date with the latest Kubernetes API.

## Adopters/Projects

* [Kerberos-Operator2](https://github.com/novakov-alexey/krb-operator2)
