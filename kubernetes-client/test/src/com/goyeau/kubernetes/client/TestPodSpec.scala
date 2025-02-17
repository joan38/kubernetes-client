package com.goyeau.kubernetes.client

import cats.syntax.all.*
import io.k8s.api.core.v1.{Container, PodSpec, ResourceRequirements}
import io.k8s.apimachinery.pkg.api.resource.Quantity

object TestPodSpec {

  val alpine: PodSpec = alpine(None)

  def alpine(command: Seq[String]): PodSpec = alpine(command.some)

  private def alpine(command: Option[Seq[String]]): PodSpec = PodSpec(
    containers = Seq(
      Container(
        name = "test",
        image = "alpine".some,
        command = command,
        imagePullPolicy = "IfNotPresent".some,
        resources = ResourceRequirements(
          requests = Map(
            "memory" -> Quantity("10Mi")
          ).some,
          limits = Map(
            "memory" -> Quantity("10Mi")
          ).some
        ).some
      )
    ),
    terminationGracePeriodSeconds = 0L.some
  )

}
