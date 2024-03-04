package com.goyeau.kubernetes.client.util.cache

import org.http4s.headers.Authorization

import scala.concurrent.duration.FiniteDuration

case class AuthorizationWithExpiration(
    expirationTimestamp: Option[FiniteDuration],
    authorization: Authorization
)
