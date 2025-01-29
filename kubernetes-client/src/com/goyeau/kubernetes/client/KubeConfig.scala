package com.goyeau.kubernetes.client

import cats.ApplicativeThrow
import cats.data.{NonEmptyList, OptionT}
import cats.effect.Async
import cats.syntax.all.*
import com.comcast.ip4s.{IpAddress, Port}
import com.goyeau.kubernetes.client.util.cache.{AuthorizationCache, AuthorizationWithExpiration}
import com.goyeau.kubernetes.client.util.{AuthInfoExec, Text, Yamls}
import fs2.io.file.{Files, Path}
import org.http4s.headers.Authorization
import org.http4s.{AuthScheme, Credentials, Uri}
import org.typelevel.log4cats.Logger

import scala.annotation.nowarn
import scala.concurrent.duration.*

@nowarn("msg=access modifiers for `copy|apply` method are copied from the case class constructor under Scala 3")
case class KubeConfig[F[_]] private (
    server: Uri,
    authorization: Option[F[Authorization]],
    caCertData: Option[String],
    caCertFile: Option[Path],
    clientCertData: Option[String],
    clientCertFile: Option[Path],
    clientKeyData: Option[String],
    clientKeyFile: Option[Path],
    clientKeyPass: Option[String],
    authInfoExec: Option[AuthInfoExec],
    authorizationCache: Option[F[AuthorizationWithExpiration] => F[F[Authorization]]]
) {

  def withAuthorizationCache(
      authorizationCache: F[AuthorizationWithExpiration] => F[F[Authorization]]
  ): KubeConfig[F] =
    this.copy(authorizationCache = authorizationCache.some)

  def withoutAuthorizationCache: KubeConfig[F] =
    this.copy(authorizationCache = none)

  def withDefaultAuthorizationCache(
      refreshTokenBeforeExpiration: FiniteDuration = 5.minutes
  )(implicit F: Async[F], L: Logger[F]): KubeConfig[F] = {
    val addCache: F[AuthorizationWithExpiration] => F[F[Authorization]] =
      retrieve => AuthorizationCache(retrieve, refreshTokenBeforeExpiration).map(_.get)

    this.copy(
      authorizationCache = addCache.some
    )
  }

}

object KubeConfig {

  private val EnvKubeConfig           = "KUBECONFIG"
  private val EnvHome                 = "HOME"
  private val EnvHomeDrive            = "HOMEDRIVE"
  private val EnvHomePath             = "HOMEPATH"
  private val EnvUserProfile          = "USERPROFILE"
  private val KubeConfigDir           = ".kube"
  private val KubeConfigFile          = "config"
  private val ServiceAccountRoot      = "/var/run/secrets/kubernetes.io/serviceaccount"
  private val ServiceAccountCAPath    = ServiceAccountRoot + "/ca.crt"
  private val ServiceAccountTokenPath = ServiceAccountRoot + "/token"
  private val EnvServiceHost          = "KUBERNETES_SERVICE_HOST"
  private val EnvServicePort          = "KUBERNETES_SERVICE_PORT"

  /** Will try to find a k8s configuration in the following order:
    *
    *   - if KUBECONFIG env variable is set, and the file exists - use it; uses the 'current-context' specified in the
    *     file
    *   - if ~/.kube/config file exists - use it; uses the 'current-context' specified in the file
    *   - if cluster configuration is found - use it
    *
    * Cluster configuration is defined by:
    *   - /var/run/secrets/kubernetes.io/serviceaccount/ca.crt certificate file
    *   - /var/run/secrets/kubernetes.io/serviceaccount/token token file
    *   - KUBERNETES_SERVICE_HOST env variable (https protocol is assumed)
    *   - KUBERNETES_SERVICE_PORT env variable
    */
  def standard[F[_]: Logger](implicit F: Async[F]): F[KubeConfig[F]] =
    findFromEnv
      .orElse(findConfigInHomeDir(none))
      .orElse(findClusterConfig)
      .getOrRaise(KubeConfigNotFoundError)

  /** Use the file specified in KUBECONFIG env variable, if exists. Uses the 'current-context' specified in the file.
    */
  def fromEnv[F[_]: Logger](implicit F: Async[F]): F[KubeConfig[F]] =
    findFromEnv
      .getOrRaise(KubeConfigNotFoundError)

  /** Uses the configuration from ~/.kube/config, if exists. Uses the 'current-context' specified in the file.
    */
  def inHomeDir[F[_]: Logger](implicit F: Async[F]): F[KubeConfig[F]] =
    findConfigInHomeDir(none)
      .getOrRaise(KubeConfigNotFoundError)

  /** Uses the configuration from ~/.kube/config, if exists. Uses the provided contextName (will fail if the context
    * does not exist).
    */
  def inHomeDir[F[_]: Logger](contextName: String)(implicit F: Async[F]): F[KubeConfig[F]] =
    findConfigInHomeDir(contextName.some)
      .getOrRaise(KubeConfigNotFoundError)

  /** Uses the cluster configuration, if found.
    *
    * Cluster configuration is defined by:
    *   - /var/run/secrets/kubernetes.io/serviceaccount/ca.crt certificate file,
    *   - /var/run/secrets/kubernetes.io/serviceaccount/token token file,
    *   - KUBERNETES_SERVICE_HOST env variable (https protocol is assumed),
    *   - KUBERNETES_SERVICE_PORT env variable.
    */
  def cluster[F[_]: Logger](implicit F: Async[F]): F[KubeConfig[F]] =
    findClusterConfig
      .getOrRaise(KubeConfigNotFoundError)

  /** Read the configuration from the specified file. Uses the provided contextName (will fail if the context does not
    * exist).
    */
  def fromFile[F[_]: Async: Logger](kubeconfig: Path): F[KubeConfig[F]] =
    Yamls.fromKubeConfigFile(kubeconfig, None)

  /** Read the configuration from the specified file. Uses the 'current-context' specified in the file.
    */
  def fromFile[F[_]: Async: Logger](kubeconfig: Path, contextName: String): F[KubeConfig[F]] =
    Yamls.fromKubeConfigFile(kubeconfig, Option(contextName))

  @deprecated(message = "Use fromFile instead", since = "0.4.1")
  def apply[F[_]: Async: Logger](kubeconfig: Path): F[KubeConfig[F]] = fromFile(kubeconfig)

  @deprecated(message = "Use fromFile instead", since = "0.4.1")
  def apply[F[_]: Async: Logger](kubeconfig: Path, contextName: String): F[KubeConfig[F]] =
    fromFile(kubeconfig, contextName)

  def of[F[_]: ApplicativeThrow](
      server: Uri,
      authorization: Option[F[Authorization]] = None,
      caCertData: Option[String] = None,
      caCertFile: Option[Path] = None,
      clientCertData: Option[String] = None,
      clientCertFile: Option[Path] = None,
      clientKeyData: Option[String] = None,
      clientKeyFile: Option[Path] = None,
      clientKeyPass: Option[String] = None,
      authInfoExec: Option[AuthInfoExec] = None
  ): F[KubeConfig[F]] = {
    val configOrError =
      List(
        (caCertData, caCertFile).tupled.isDefined ->
          "caCertData and caCertFile cannot be specified at the same time",
        (clientCertData, clientCertFile).tupled.isDefined ->
          "clientCertData and clientCertFile cannot be specified at the same time",
        (clientKeyData, clientKeyFile).tupled.isDefined ->
          "clientKeyData and clientKeyFile cannot be specified at the same time",
        (authorization, authInfoExec).tupled.isDefined ->
          "authorization and authInfoExec cannot be specified at the same time",
        authInfoExec.exists(_.interactiveMode.contains("Always")) ->
          "interactiveMode=Always is not supported",
        authInfoExec.exists(_.provideClusterInfo.contains(true)) ->
          "provideClusterInfo=true is not supported"
      ).map { case (badCondition, error) =>
        Either.cond(!badCondition, (), NonEmptyList.one(error))
      }.parSequence
        .leftMap(errors => new IllegalArgumentException(errors.toList.mkString("; ")))
        .as {
          KubeConfig(
            server = server,
            authorization = authorization,
            caCertData = caCertData,
            caCertFile = caCertFile,
            clientCertData = clientCertData,
            clientCertFile = clientCertFile,
            clientKeyData = clientKeyData,
            clientKeyFile = clientKeyFile,
            clientKeyPass = clientKeyPass,
            authInfoExec = authInfoExec,
            authorizationCache = none
          )
        }

    ApplicativeThrow[F].fromEither(configOrError)
  }

  private def findFromEnv[F[_]: Logger](implicit F: Async[F]): OptionT[F, KubeConfig[F]] =
    envPath[F](EnvKubeConfig)
      .flatMapF(checkExists(_))
      .flatTapNone {
        Logger[F].debug(s"$EnvKubeConfig is not defined, or path does not exist")
      }
      .semiflatTap { path =>
        Logger[F].debug(s"using configuration specified by $EnvKubeConfig=$path")
      }
      .semiflatMap(fromFile(_))

  private def findConfigInHomeDir[F[_]: Logger](
      contextName: Option[String]
  )(implicit F: Async[F]): OptionT[F, KubeConfig[F]] =
    findHomeDir
      .map(homeDir => homeDir.resolve(KubeConfigDir).resolve(KubeConfigFile))
      .flatMapF(checkExists(_))
      .flatTapNone {
        Logger[F].debug(s"~/$KubeConfigDir/$KubeConfigFile does not exist")
      }
      .semiflatTap { path =>
        Logger[F].debug(s"using configuration specified in $path")
      }
      .semiflatMap(path => contextName.fold(fromFile(path))(fromFile(path, _)))

  private def findClusterConfig[F[_]: Logger](implicit F: Async[F]): OptionT[F, KubeConfig[F]] =
    (
      path(ServiceAccountTokenPath)
        .flatMapF(checkExists(_))
        .flatTapNone {
          Logger[F].debug(s"$ServiceAccountTokenPath does not exist")
        },
      path(ServiceAccountCAPath)
        .flatMapF(checkExists(_))
        .flatTapNone {
          Logger[F].debug(s"$ServiceAccountCAPath does not exist")
        },
      env(EnvServiceHost)
        .mapFilter(IpAddress.fromString)
        .map(Uri.Host.fromIpAddress)
        .flatTapNone {
          Logger[F].debug(s"$EnvServiceHost is not defined, or not a valid IP address")
        },
      env(EnvServicePort)
        .mapFilter(Port.fromString)
        .flatTapNone {
          Logger[F].debug(s"$EnvServicePort is not defined, or not a valid port number")
        }
    ).tupled
      .semiflatTap { case (tokenPath, caPath, serviceHost, servicePort) =>
        Logger[F].debug(
          s"using the in-cluster configuration: $EnvServiceHost=$serviceHost, $EnvServicePort=$servicePort, $ServiceAccountTokenPath=$tokenPath, $ServiceAccountCAPath=$caPath"
        )
      }
      .semiflatMap { case (tokenPath, caPath, serviceHost, servicePort) =>
        of(
          server = Uri(
            scheme = Uri.Scheme.https.some,
            authority = Uri.Authority(host = serviceHost, port = servicePort.value.some).some
          ),
          authorization =
            Text.readFile(tokenPath).map(token => Authorization(Credentials.Token(AuthScheme.Bearer, token))).some,
          caCertFile = caPath.some
        )
      }

  private def findHomeDir[F[_]: Logger](implicit F: Async[F]): OptionT[F, Path] =
    OptionT.liftF(
      Logger[F].debug(s"finding the home directory")
    ) >>
      envPath(EnvHome) // if HOME env var is set, use it
        .flatMapF(checkExists(_))
        .flatTapNone(
          Logger[F].debug(s"$EnvHome is not defined, or path does not exist")
        )
        .orElse {
          // otherwise, if it's a windows machine
          sysProp("os.name")
            .filter(_.toLowerCase().startsWith("windows"))
            .flatMap { _ =>
              // if HOMEDRIVE and HOMEPATH env vars are set and the path exists, use it
              (env(EnvHomeDrive), envPath(EnvHomePath)).tupled
                .map { case (homeDrive, homePath) => Path(homeDrive).resolve(homePath) }
                .flatMapF(checkExists(_))
                .flatTapNone(
                  Logger[F].debug(s"$EnvHomeDrive and/or $EnvHomePath is/are not defined, or path does not exist")
                )
                .orElse {
                  // otherwise, of USERPROFILE env var is set
                  envPath(EnvUserProfile)
                    .flatMapF(checkExists(_))
                    .flatTapNone(
                      Logger[F].debug(s"$EnvUserProfile is not defined, or path does not exist")
                    )
                }
            }
        }

  private def sysProp[F[_]](name: String)(implicit F: Async[F]): OptionT[F, String] =
    OptionT(F.delay(Option(System.getProperty(name)).filterNot(_.isEmpty)))

  private def env[F[_]](name: String)(implicit F: Async[F]): OptionT[F, String] =
    OptionT(F.delay(Option(System.getenv(name)).filterNot(_.isEmpty)))

  private def path[F[_]: Async](path: String): OptionT[F, Path] =
    OptionT.pure[F](Path(path))

  private def envPath[F[_]: Async](name: String): OptionT[F, Path] =
    env(name).map(Path(_))

  private def checkExists[F[_]: Async](path: Path): F[Option[Path]] =
    Files[F].exists(path).map(if (_) Option(path) else none) // Option.when does not exist in scala 2.12

}
