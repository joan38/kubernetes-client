package com.goyeau.kubernetes.client.cache

import cats.syntax.all.*
import cats.effect.*
import com.goyeau.kubernetes.client.util.cache.{AuthorizationCache, AuthorizationWithExpiration}
import org.typelevel.log4cats.Logger
import com.goyeau.kubernetes.client.TestPlatformSpecific
import munit.CatsEffectSuite
import org.http4s.{AuthScheme, Credentials}
import org.http4s.headers.Authorization
import java.time.Instant
import scala.concurrent.duration.*

class AuthorizationCacheTest extends CatsEffectSuite {

  implicit lazy val logger: Logger[IO] = TestPlatformSpecific.getLogger

  private def mkAuthorization(
      expirationTimestamp: IO[Option[Instant]] = none.pure[IO],
      token: IO[String] = s"test-token".pure[IO]
  ): IO[AuthorizationWithExpiration] =
    token.flatMap { token =>
      expirationTimestamp.map { expirationTimestamp =>
        AuthorizationWithExpiration(
          expirationTimestamp = expirationTimestamp,
          authorization = Authorization(Credentials.Token(AuthScheme.Bearer, token))
        )
      }
    }

  test(s"retrieve the token initially") {
    for {
      auth     <- mkAuthorization()
      cache    <- AuthorizationCache[IO](retrieve = auth.pure[IO])
      obtained <- cache.get
    } yield assertEquals(obtained, auth.authorization)
  }

  test(s"fail when cannot retrieve the token initially") {
    for {
      cache    <- AuthorizationCache[IO](retrieve = IO.raiseError(new RuntimeException("test failure")))
      obtained <- cache.get.attempt
    } yield assert(obtained.isLeft)    
  }

  test(s"retrieve the token once when no expiration") {
    for {
      counter <- IO.ref(1)
      auth = mkAuthorization(token = counter.getAndUpdate(_ + 1).map(i => s"test-token-$i"))
      cache    <- AuthorizationCache[IO](retrieve = auth)
      obtained <- (1 to 5).toList.traverse(i => cache.get.product(i.pure[IO]))
    } yield obtained.foreach { case (obtained, _) =>
      assertEquals(obtained, Authorization(Credentials.Token(AuthScheme.Bearer, s"test-token-1")))
    }
  }

  test(s"retrieve the token when it's expired") {
    for {
      counter <- IO.ref(1)
      auth = mkAuthorization(
        expirationTimestamp = IO.realTime.map(d => Instant.ofEpochMilli(d.minus(10.seconds).toMillis).some),
        token = counter.getAndUpdate(_ + 1).map(i => s"test-token-$i")
      )
      cache    <- AuthorizationCache[IO](retrieve = auth)
      obtained <- (1 to 5).toList.traverse(i => cache.get.product(i.pure[IO]))
    } yield obtained.foreach { case (obtained, i) =>
      assertEquals(obtained, Authorization(Credentials.Token(AuthScheme.Bearer, s"test-token-$i")))
    }
  }

  test(s"retrieve the token when it's going to expire within refreshBeforeExpiration") {
    for {
      counter <- IO.ref(1)
      auth = mkAuthorization(
        expirationTimestamp = IO.realTime.map(d => Instant.ofEpochMilli(d.plus(40.seconds).toMillis).some),
        token = counter.getAndUpdate(_ + 1).map(i => s"test-token-$i")
      )
      cache    <- AuthorizationCache[IO](retrieve = auth, refreshBeforeExpiration = 1.minute)
      obtained <- (1 to 5).toList.traverse(i => cache.get.product(i.pure[IO]))
    } yield obtained.foreach { case (obtained, i) =>
      assertEquals(obtained, Authorization(Credentials.Token(AuthScheme.Bearer, s"test-token-$i")))
    }
  }

  test(s"fail if cannot retrieve the token when it's expired") {
    for {
      counter    <- IO.ref(1)
      shouldFail <- IO.ref(false)
      auth = mkAuthorization(
        expirationTimestamp = IO.realTime.map(d => Instant.ofEpochMilli(d.minus(10.seconds).toMillis).some),
        token = shouldFail.get.flatMap { shouldFail =>
          if (shouldFail) {
            IO.raiseError(new RuntimeException("test failure"))
          } else {
            counter.getAndUpdate(_ + 1).map(i => s"test-token-$i")
          }
        }
      )
      cache          <- AuthorizationCache[IO](retrieve = auth)
      obtained       <- (1 to 5).toList.traverse(i => cache.get.product(i.pure[IO]))
      _              <- shouldFail.set(true)
      obtainedFailed <- (1 to 5).toList.traverse(i => cache.get.attempt.product(i.pure[IO]))
    } yield {
      obtained.foreach { case (obtained, i) =>
        assertEquals(obtained, Authorization(Credentials.Token(AuthScheme.Bearer, s"test-token-$i")))
      }
      obtainedFailed.foreach { case (obtained, _) =>
        println(obtained)
        assert(obtained.isLeft)
      }
    }
  }

  test(s"fail if cannot retrieve the token when it's expired, then recover") {
    for {
      counter    <- IO.ref(1)
      shouldFail <- IO.ref(false)
      auth = mkAuthorization(
        expirationTimestamp = IO.realTime.map(d => Instant.ofEpochMilli(d.minus(10.seconds).toMillis).some),
        token = shouldFail.get.flatMap { shouldFail =>
          if (shouldFail) {
            IO.raiseError(new RuntimeException("test failure"))
          } else {
            counter.getAndUpdate(_ + 1).map(i => s"test-token-$i")
          }
        }
      )
      cache          <- AuthorizationCache[IO](retrieve = auth)
      obtained       <- (1 to 5).toList.traverse(i => cache.get.product(i.pure[IO]))
      _              <- shouldFail.set(true)
      obtainedFailed <- (1 to 5).toList.traverse(i => cache.get.attempt.product(i.pure[IO]))
      _              <- shouldFail.set(false)
      obtainedAgain  <- (6 to 10).toList.traverse(i => cache.get.attempt.product(i.pure[IO]))
    } yield {
      obtained.foreach { case (obtained, i) =>
        assertEquals(obtained, Authorization(Credentials.Token(AuthScheme.Bearer, s"test-token-$i")))
      }
      obtainedFailed.foreach { case (obtained, _) =>
        println(obtained)
        assert(obtained.isLeft)
      }
      obtainedAgain.foreach { case (obtained, i) =>
        assertEquals(obtained, Authorization(Credentials.Token(AuthScheme.Bearer, s"test-token-$i")).asRight)
      }
    }
  }

}
