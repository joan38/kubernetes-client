package com.goyeau.kubernetes.client

case object KubeConfigNotFoundError extends RuntimeException("Kubernetes configuration not found")
