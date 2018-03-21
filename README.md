# Kubernetes Client

[![Latest version](https://index.scala-lang.org/joan38/kubernetes-client/kubernetes-client/latest.svg?color=blue)](https://index.scala-lang.org/joan38/kubernetes-client/kubernetes-client)


## Installation
```scala
"com.goyeau" %% "kubernetes-client" % "<latest version>"
```


## Usage

### Client configuration
```scala
import scala.io.Source
import java.io.File
import com.goyeau.kubernetesclient._

val client = KubernetesClient(
  KubeConfig(
    server = "https://k8s.goyeau.com",
    oauthToken = Option(Source.fromFile("/var/run/secrets/kubernetes.io/serviceaccount/token").mkString),
    caCertFile = Option(new File("/var/run/secrets/kubernetes.io/serviceaccount/ca.crt"))
  )
)
```

```scala
import java.io.File
import com.goyeau.kubernetesclient._

val client = KubernetesClient(KubeConfig(new File("/opt/docker/secrets/kube/config")))
```

### Requests

```scala
import java.io.File
import com.goyeau.kubernetesclient._
import io.k8s.api.apps.v1beta1.{Deployment, DeploymentSpec, DeploymentStrategy, RollingUpdateDeployment}
import io.k8s.api.core.v1._
import io.k8s.apimachinery.pkg.api.resource.Quantity
import io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta
import io.k8s.apimachinery.pkg.util.intstr.IntOrString

val client = KubernetesClient(KubeConfig(new File("/opt/docker/secrets/kube/config")))

val deployment = Deployment(
  metadata = Option(ObjectMeta(name = Option("web-backend"), namespace = Option("my-namespace"))),
  spec = Option(
    DeploymentSpec(
      strategy = Option(
        DeploymentStrategy(
          `type` = Option("RollingUpdate"),
          rollingUpdate = Option(RollingUpdateDeployment(Option(IntOrString("10%")), Option(IntOrString("50%"))))
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
                image = "nginx",
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

client.deployments.namespace("my-namespace").create(deployment)
```


## Related projects

* [Skuber](https://github.com/doriordan/skuber)
* [Kubernetes Client for Java](https://github.com/fabric8io/kubernetes-client)
