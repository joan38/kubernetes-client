package com.goyeau.kubernetes.client.api

trait LabelSelector[A] {
  val labels: Map[String, String]
  def withLabels(labels: Map[String, String]): A
}
