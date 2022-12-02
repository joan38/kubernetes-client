package com.goyeau.kubernetes.client.util.cache

import org.http4s.headers.Authorization

import java.time.Instant

private[client] case class CachedAuthorization(
    expirationTimestamp: Option[Instant],
    token: Authorization
)
