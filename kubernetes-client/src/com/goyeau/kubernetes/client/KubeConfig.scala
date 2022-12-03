package com.goyeau.kubernetes.client

import cats.ApplicativeThrow
import cats.data.{NonEmptyList, OptionT}
import cats.effect.Async
import cats.syntax.all.*
import com.comcast.ip4s.{IpAddress, Port}
import com.goyeau.kubernetes.client.util.{AuthInfoExec, Text, Yamls}
import fs2.io.file.{Files, Path}
import org.http4s.headers.Authorization
import org.http4s.{AuthScheme, Credentials, Uri}
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.*

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
    refreshTokenBeforeExpiration: FiniteDuration = 1.minute
) {

  def withRefreshTokenBeforeExpiration(refreshTokenBeforeExpiration: FiniteDuration): KubeConfig[F] =
    this.copy(refreshTokenBeforeExpiration = refreshTokenBeforeExpiration)

}

object KubeConfig {

  private val ENV_KUBECONFIG            = "KUBECONFIG"
  private val ENV_HOME                  = "HOME"
  private val ENV_HOMEDRIVE             = "HOMEDRIVE"
  private val ENV_HOMEPATH              = "HOMEPATH"
  private val ENV_USERPROFILE           = "USERPROFILE"
  private val KUBEDIR                   = ".kube"
  private val KUBECONFIG                = "config"
  private val SERVICEACCOUNT_ROOT       = "/var/run/secrets/kubernetes.io/serviceaccount"
  private val SERVICEACCOUNT_CA_PATH    = SERVICEACCOUNT_ROOT + "/ca.crt"
  private val SERVICEACCOUNT_TOKEN_PATH = SERVICEACCOUNT_ROOT + "/token"
  private val ENV_SERVICE_HOST          = "KUBERNETES_SERVICE_HOST"
  private val ENV_SERVICE_PORT          = "KUBERNETES_SERVICE_PORT"

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
    *
    * @return
    *   a KubeConfig[F] if able to find one.
    */
  def standard[F[_]: Logger](implicit F: Async[F]): F[Option[KubeConfig[F]]] =
    findFromEnv
      .orElse(findConfigInHomeDir(none))
      .orElse(findClusterConfig)
      .value

  /** Use the file specified in KUBECONFIG env variable, if exists. Uses the 'current-context' specified in the file.
    */
  def fromEnv[F[_]: Logger](implicit F: Async[F]): F[Option[KubeConfig[F]]] =
    findFromEnv.value

  /** Uses the configuration from ~/.kube/config, if exists. Uses the 'current-context' specified in the file.
    */
  def inHomeDir[F[_]: Logger](implicit F: Async[F]): F[Option[KubeConfig[F]]] =
    findConfigInHomeDir(none).value

  /** Uses the configuration from ~/.kube/config, if exists. Uses the provided contextName (will fail if the context
    * does not exist).
    */
  def inHomeDir[F[_]: Logger](contextName: String)(implicit F: Async[F]): F[Option[KubeConfig[F]]] =
    findConfigInHomeDir(contextName.some).value

  /** Uses the cluster configuration, if found.
    *
    * Cluster configuration is defined by:
    *   - /var/run/secrets/kubernetes.io/serviceaccount/ca.crt certificate file,
    *   - /var/run/secrets/kubernetes.io/serviceaccount/token token file,
    *   - KUBERNETES_SERVICE_HOST env variable (https protocol is assumed),
    *   - KUBERNETES_SERVICE_PORT env variable.
    */
  def cluster[F[_]: Logger](implicit F: Async[F]): F[Option[KubeConfig[F]]] =
    findClusterConfig.value

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
      authInfoExec: Option[AuthInfoExec] = None,
      refreshTokenBeforeExpiration: FiniteDuration = 1.minute
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
            refreshTokenBeforeExpiration = refreshTokenBeforeExpiration
          )
        }

    ApplicativeThrow[F].fromEither(configOrError)
  }

  private def findFromEnv[F[_]: Logger](implicit F: Async[F]): OptionT[F, KubeConfig[F]] =
    envPath[F](ENV_KUBECONFIG)
      .flatMapF(checkExists(_))
      .semiflatMap(fromFile(_))

  private def findConfigInHomeDir[F[_]: Logger](
      contextName: Option[String]
  )(implicit F: Async[F]): OptionT[F, KubeConfig[F]] =
    findHomeDir
      .map(homeDir => homeDir.resolve(KUBEDIR).resolve(KUBECONFIG))
      .flatMapF(checkExists(_))
      .semiflatMap(path => contextName.fold(fromFile(path))(fromFile(path, _)))

  private def findClusterConfig[F[_]: Logger](implicit F: Async[F]): OptionT[F, KubeConfig[F]] =
    (
      envPath(SERVICEACCOUNT_CA_PATH).flatMapF(checkExists(_)),
      env(ENV_SERVICE_HOST).mapFilter(IpAddress.fromString).map(Uri.Host.fromIpAddress),
      env(ENV_SERVICE_PORT).mapFilter(Port.fromString),
      envPath(SERVICEACCOUNT_TOKEN_PATH).flatMapF(checkExists(_))
    ).tupled.semiflatMap { case (caPath, serviceHost, servicePort, tokenPath) =>
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
    envPath(ENV_HOME) // if HOME env var is set, use it
      .flatMapF(checkExists(_))
      .orElse {
        // otherwise, if it's a windows machine
        sysProp("os.name")
          .filter(_.toLowerCase().startsWith("windows"))
          .flatMap { _ =>
            // if HOMEDRIVE and HOMEPATH env vars are set and the path exists, use it
            (env(ENV_HOMEDRIVE), envPath(ENV_HOMEPATH)).tupled
              .map { case (homeDrive, homePath) => Path(homeDrive).resolve(homePath) }
              .flatMapF(checkExists(_))
              .orElse {
                // otherwise, of USERPROFILE env var is set
                envPath(ENV_USERPROFILE).flatMapF(checkExists(_))
              }
          }
      }

  private def sysProp[F[_]](name: String)(implicit F: Async[F]): OptionT[F, String] =
    OptionT(F.delay(Option(System.getProperty(name)).filterNot(_.isEmpty)))

  private def env[F[_]](name: String)(implicit F: Async[F]): OptionT[F, String] =
    OptionT(F.delay(Option(System.getenv(name)).filterNot(_.isEmpty)))

  private def envPath[F[_]: Async](name: String): OptionT[F, Path] =
    env(name).map(Path(_))

  private def checkExists[F[_]: Async](path: Path): F[Option[Path]] =
    Files[F].exists(path).map(Option.when(_)(path))

}
