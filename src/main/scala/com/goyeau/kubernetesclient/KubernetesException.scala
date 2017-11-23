package com.goyeau.kubernetesclient

class KubernetesException(val statusCode: Int, message: String) extends Exception(message)
