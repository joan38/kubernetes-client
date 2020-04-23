package com.goyeau.kubernetes.client.api

trait Filterable[A] {
  val labels: Map[String, String]
  def withLabels(labels: Map[String, String]): A
}
