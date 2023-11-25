package com.goyeau.kubernetes.client

import org.http4s.client.Client
import org.http4s.client.websocket.WSClient

private case class Clients[F[_]](httpClient: Client[F], wsClient: WSClient[F])