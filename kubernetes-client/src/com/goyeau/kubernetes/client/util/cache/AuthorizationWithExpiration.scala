package com.goyeau.kubernetes.client.util.cache

import org.http4s.headers.Authorization

import java.time.Instant

case class AuthorizationWithExpiration(
    expirationTimestamp: Option[Instant],
    authorization: Authorization
)
