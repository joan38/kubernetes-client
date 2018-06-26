package com.goyeau.kubernetes.client

import akka.actor.ActorSystem
import io.circe._
import io.k8s.api.core.v1.{Namespace, NamespaceList}

private[client] case class NamespacesOperations(protected val config: KubeConfig)(
  implicit protected val system: ActorSystem,
  protected val listDecoder: Decoder[NamespaceList],
  protected val resourceEncoder: Encoder[Namespace],
  protected val resourceDecoder: Decoder[Namespace]
) extends Creatable[Namespace]
    with Replaceable[Namespace]
    with Gettable[Namespace]
    with Listable[NamespaceList]
    with Deletable {
  protected val resourceUri = "api/v1/namespaces"
}
