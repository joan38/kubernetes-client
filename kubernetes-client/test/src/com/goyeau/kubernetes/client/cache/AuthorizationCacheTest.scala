package com.goyeau.kubernetes.client.cache

import cats.syntax.all.*
import cats.effect.*
import com.goyeau.kubernetes.client.util.cache.{AuthorizationCache, AuthorizationWithExpiration}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import munit.FunSuite
import org.http4s.{AuthScheme, Credentials}
import org.http4s.headers.Authorization
import cats.effect.unsafe.implicits.global
import java.time.Instant
import scala.concurrent.duration.*

class AuthorizationCacheTest extends FunSuite {

  implicit lazy val F: Async[IO]       = IO.asyncForIO
  implicit lazy val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private def mkAuthorization(
      expirationTimestamp: IO[Option[Instant]] = none.pure,
      token: IO[String] = s"test-token".pure
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
    val io = for {
      auth     <- mkAuthorization()
      cache    <- AuthorizationCache[IO](retrieve = auth.pure)
      obtained <- cache.get
    } yield assertEquals(obtained, auth.authorization)
    io.unsafeRunSync()
  }

  test(s"fail when cannot retrieve the token initially") {
    val io = for {
      cache    <- AuthorizationCache[IO](retrieve = IO.raiseError(new RuntimeException("test failure")))
      obtained <- cache.get.attempt
    } yield assert(obtained.isLeft)
    io.unsafeRunSync()
  }

  test(s"retrieve the token once when no expiration") {
    val io = for {
      counter <- IO.ref(1)
      auth = mkAuthorization(token = counter.getAndUpdate(_ + 1).map(i => s"test-token-$i"))
      cache    <- AuthorizationCache[IO](retrieve = auth)
      obtained <- (1 to 5).toList.traverse(i => cache.get.product(i.pure))
    } yield obtained.foreach { case (obtained, _) =>
      assertEquals(obtained, Authorization(Credentials.Token(AuthScheme.Bearer, s"test-token-1")))
    }
    io.unsafeRunSync()
  }

  test(s"retrieve the token when it's expired") {
    val io = for {
      counter <- IO.ref(1)
      auth = mkAuthorization(
        expirationTimestamp = IO.realTimeInstant.map(_.minusSeconds(10).some),
        token = counter.getAndUpdate(_ + 1).map(i => s"test-token-$i")
      )
      cache    <- AuthorizationCache[IO](retrieve = auth)
      obtained <- (1 to 5).toList.traverse(i => cache.get.product(i.pure))
    } yield obtained.foreach { case (obtained, i) =>
      assertEquals(obtained, Authorization(Credentials.Token(AuthScheme.Bearer, s"test-token-$i")))
    }
    io.unsafeRunSync()
  }

  test(s"retrieve the token when it's going to expire within refreshBeforeExpiration") {
    val io = for {
      counter <- IO.ref(1)
      auth = mkAuthorization(
        expirationTimestamp = IO.realTimeInstant.map(_.plusSeconds(40).some),
        token = counter.getAndUpdate(_ + 1).map(i => s"test-token-$i")
      )
      cache    <- AuthorizationCache[IO](retrieve = auth, refreshBeforeExpiration = 1.minute)
      obtained <- (1 to 5).toList.traverse(i => cache.get.product(i.pure))
    } yield obtained.foreach { case (obtained, i) =>
      assertEquals(obtained, Authorization(Credentials.Token(AuthScheme.Bearer, s"test-token-$i")))
    }
    io.unsafeRunSync()
  }

  test(s"fail if cannot retrieve the token when it's expired") {
    val io = for {
      counter    <- IO.ref(1)
      shouldFail <- IO.ref(false)
      auth = mkAuthorization(
        expirationTimestamp = IO.realTimeInstant.map(_.minusSeconds(10).some),
        token = shouldFail.get.flatMap { shouldFail =>
          if (shouldFail)
            IO.raiseError(new RuntimeException("test failure"))
          else
            counter.getAndUpdate(_ + 1).map(i => s"test-token-$i")
        }
      )
      cache          <- AuthorizationCache[IO](retrieve = auth)
      obtained       <- (1 to 5).toList.traverse(i => cache.get.product(i.pure))
      _              <- shouldFail.set(true)
      obtainedFailed <- (1 to 5).toList.traverse(i => cache.get.attempt.product(i.pure))
    } yield {
      obtained.foreach { case (obtained, i) =>
        assertEquals(obtained, Authorization(Credentials.Token(AuthScheme.Bearer, s"test-token-$i")))
      }
      obtainedFailed.foreach { case (obtained, _) =>
        println(obtained)
        assert(obtained.isLeft)
      }
    }
    io.unsafeRunSync()
  }

  test(s"fail if cannot retrieve the token when it's expired, then recover") {
    val io = for {
      counter    <- IO.ref(1)
      shouldFail <- IO.ref(false)
      auth = mkAuthorization(
        expirationTimestamp = IO.realTimeInstant.map(_.minusSeconds(10).some),
        token = shouldFail.get.flatMap { shouldFail =>
          if (shouldFail)
            IO.raiseError(new RuntimeException("test failure"))
          else
            counter.getAndUpdate(_ + 1).map(i => s"test-token-$i")
        }
      )
      cache          <- AuthorizationCache[IO](retrieve = auth)
      obtained       <- (1 to 5).toList.traverse(i => cache.get.product(i.pure))
      _              <- shouldFail.set(true)
      obtainedFailed <- (1 to 5).toList.traverse(i => cache.get.attempt.product(i.pure))
      _              <- shouldFail.set(false)
      obtainedAgain  <- (6 to 10).toList.traverse(i => cache.get.attempt.product(i.pure))
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
    io.unsafeRunSync()
  }

}
