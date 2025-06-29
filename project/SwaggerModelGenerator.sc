import $file.Model
import Model.{Definition, Property}
import $ivy.`io.circe::circe-core:0.14.10`
import $ivy.`io.circe::circe-generic:0.14.10`
import $ivy.`io.circe::circe-parser:0.14.10`
import mill._
import mill.api.Logger
import mill.define.{Discover, ExternalModule, Sources}
import mill.scalalib._
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import os._

trait SwaggerModelGenerator extends JavaModule {
  import SwaggerModelGenerator._

  def kubernetesSwagger: T[String]

  override def generatedSources = T {
    super.generatedSources() ++
      processSwaggerFile(kubernetesSwagger(), T.ctx().dest, T.ctx().log).map(PathRef(_))
  }
}

object SwaggerModelGenerator {
  val skipClasses = Set(
    "io.k8s.apimachinery.pkg.apis.meta.v1.WatchEvent",
    "io.k8s.apiextensions-apiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrBool",
    "io.k8s.apiextensions-apiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrStringArray",
    "io.k8s.apiextensions-apiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrArray",
    "io.k8s.apiextensions-apiserver.pkg.apis.apiextensions.v1.JSON",
    "io.k8s.apimachinery.pkg.runtime.RawExtension"
  )

  val existingClassesPrefix = "com.goyeau.kubernetes.client"

  def classNameFilter(className: String): Boolean = {
    val allowedPrefixes = Seq(
      "io.k8s.api.apps.v1",
      "io.k8s.api.core.v1",
      "io.k8s.api.rbac.v1",
      "io.k8s.api.batch.v1",
      "io.k8s.api.policy.v1",
      "io.k8s.apimachinery.pkg.runtime",
      "io.k8s.api.storage.v1",
      "io.k8s.api.autoscaling.v1",
      "io.k8s.apimachinery.pkg.api",
      "io.k8s.kubernetes.pkg.apis.storage.v1",
      "io.k8s.apimachinery.pkg.apis.meta.v1",
      "io.k8s.kubernetes.pkg.api.v1",
      "io.k8s.kubernetes.pkg.apis.batch.v1",
      "io.k8s.kubernetes.pkg.apis.networking.v1",
      "io.k8s.apiextensions-apiserver.pkg.apis.apiextensions.v1.CustomResource",
      "io.k8s.apiextensions-apiserver.pkg.apis.apiextensions.v1.WebhookConversion",
      "io.k8s.apiextensions-apiserver.pkg.apis.apiextensions.v1.JSONSchemaProps",
      "io.k8s.apiextensions-apiserver.pkg.apis.apiextensions.v1.ExternalDocumentation",
      "io.k8s.apiextensions-apiserver.pkg.apis.apiextensions.v1.WebhookClientConfig",
      "io.k8s.apiextensions-apiserver.pkg.apis.apiextensions.v1.SelectableField",
      "io.k8s.apiextensions-apiserver.pkg.apis.apiextensions.v1.ServiceReference",
      "io.k8s.apiextensions-apiserver.pkg.apis.apiextensions.v1.ValidationRule",
      "io.k8s.api.networking.v1",
      "io.k8s.api.coordination.v1"
    )
    allowedPrefixes.exists(className.startsWith) && !skipClasses.contains(className)
  }

  def processSwaggerFile(kubernetesSwagger: String, outputDir: Path, log: Logger): Seq[Path] = {
    val json = parse(kubernetesSwagger).fold(throw _, identity)
    for {
      definitionsJson   <- json.hcursor.downField("definitions").focus.toSeq
      definitionsObject <- definitionsJson.asObject.toSeq

      (fullClassName, definition) <- definitionsObject.toMap
      if classNameFilter(fullClassName)
    } yield generateDefinition(fullClassName, definition, outputDir, log)
  }

  def generateDefinition(fullClassName: String, definitionJson: Json, outputDir: Path, log: Logger): Path = {
    val split       = fullClassName.split("\\.")
    val packageName = sanitizeClassPath(split.init.mkString("."))
    val className   = split.last
    val output      = outputDir / RelPath(sanitizeClassPath(split.init.mkString("/"))) / s"$className.scala"

    val generatedClass = definitionJson.as[Definition].fold(throw _, identity) match {
      case Definition(desc, required, properties, Some("object"), _) =>
        val description   = generateScalaDocDescription(desc)
        val attributes    = generateAttributes(properties.toSeq.flatten.sortBy(_._1), required.toSeq.flatten)
        val scalaDocLines = generateScalaDocLines(properties.toSeq.flatten.sortBy(_._1), required.toSeq.flatten)
        val scalaDoc      = scalaDocLines.mkString(" *  ", "\n *  ", "")
        s"""import io.circe.*
           |import io.circe.generic.semiauto.*
           |
           |/** $description
           | *
           |$scalaDoc
           |*/
           |case class $className(
           |  ${attributes.replace("\n", "\n  ")}
           |)
           |
           |object $className {
           |  implicit lazy val encoder: Encoder.AsObject[$className] = deriveEncoder
           |  implicit lazy val decoder: Decoder[$className] = deriveDecoder
           |}
           |""".stripMargin

      case Definition(_, None, None, Some(t), _) =>
        val scalaType = swaggerToScalaType(t)
        s"""import io.circe.*
           |
           |case class $className(value: $scalaType) extends AnyVal
           |
           |object $className {
           |  implicit val encoder: Encoder[$className] = obj => Json.from$scalaType(obj.value)
           |  implicit val decoder: Decoder[$className] = _.as[$scalaType].map($className(_))
           |}""".stripMargin

      case d => sys.error(s"Unsupported definition for $fullClassName: $d")
    }

    write(
      output,
      s"""package $packageName
         |
         |$generatedClass""".stripMargin,
      createFolders = true
    )
    log.info(s"Generated $output")
    output
  }

  def generateAttributes(properties: Iterable[(String, Property)], required: Seq[String]): String =
    properties.toSeq
      .sortBy { case (name, _) =>
        if (required.contains(name)) required.indexOf(name)
        else Int.MaxValue
      }
      .map { case (name, property) =>
        val description =
          generateDescription(property.description).split("\n").map(_.trim).filterNot(_.isEmpty).mkString("\n")
        val escapedName = escapeAttributeName(name)
        val classPath   =
          if (required.contains(name)) generateType(property)
          else s"Option[${generateType(property)}] = None"
        s"""$escapedName: $classPath"""
      }
      .mkString(",\n")

  def maxLength(s: String, maxLenFirst: Int, maxLenRest: Int): List[String] = {
    var work  = s
    val lines = scala.collection.mutable.ListBuffer.empty[String]
    while (work.nonEmpty) {
      val maxLen = if (lines.isEmpty) maxLenFirst else maxLenRest
      if (work.length <= maxLen) {
        lines.append(work)
        work = ""
      } else
        (2 to 20).flatMap { lookBehind =>
          Seq(' ', ',', '.', ';')
            .flatMap { c =>
              val idx = work.indexOf(c, maxLen - lookBehind)
              Option.when(
                idx != -1 && (c.isWhitespace || idx == work.length - 1 || work(idx + 1).isWhitespace)
              )(idx)
            }
        }.headOption match {
          case Some(cutAt) =>
            lines.append(work.substring(0, cutAt).trim)
            work = work.substring(cutAt + 1).trim
          case None =>
            lines.append(work)
            work = ""
        }
    }
    lines.toList
  }

  def generateScalaDocLines(properties: Iterable[(String, Property)], required: Seq[String]): Seq[String] = {
    val longestName = properties.map(_._1).map(escapeAttributeName).map(_.length).maxOption.getOrElse(0)
    properties.toSeq
      .sortBy { case (name, _) =>
        if (required.contains(name)) required.indexOf(name)
        else Int.MaxValue
      }
      .flatMap { case (name, property) =>
        val description = generateScalaDocDescription(property.description)
        val escapedName = escapeAttributeName(name).reverse.padTo(longestName, ' ').reverse
        val prefix      = s"@param $escapedName "
        val lines       = s"""$prefix $description"""
          .split('\n')
          .toSeq
          .map(_.trim)
          .filterNot(_.isEmpty)
          .zipWithIndex
          .flatMap { case (l, idx) => maxLength(l, if (idx == 0) 120 else 120 - prefix.length, 120 - prefix.length) }
        lines.take(1) ++ lines.drop(1).map(l => " " * (prefix.length + 1) + l)
      }
  }

  def escapeAttributeName(name: String): String =
    if (name.contains("x-")) s"`$name`"
    else
      name
        .replace("type", "`type`")
        .replace("class", "`class`")
        .replace("object", "`object`")
        .replace("enum", "`enum`")

  def generateDescription(description: Option[String]): String =
    description.fold("")(d => s"/* ${d.replace("*/", "*&#47;").replace("/*", "&#47;*")} */\n")

  def generateScalaDocDescription(description: Option[String]): String =
    description.fold("")(d => s"${d.replace("*/", "*&#47;").replace("/*", "&#47;*")}")

  def generateType(property: Property): String =
    (property.`type`, property.$ref) match {
      case (Some(t), None) =>
        swaggerToScalaType(t, property.items.orElse(property.additionalProperties), property.format)
      case (None, Some(ref)) => sanitizeClassPath(ref)
      case _ => throw new IllegalArgumentException(s"Either the type or the ref should be set on property: $property")
    }

  def swaggerToScalaType(
      swaggerType: String,
      subProperty: Option[Property] = None,
      format: Option[String] = None
  ): String =
    (swaggerType, subProperty) match {
      case ("integer", None) =>
        format match {
          case Some("int32") => "Int"
          case Some("int64") => "Long"
          case f             => sys.error(s"Unsupported format '$f' for swaggerType '$swaggerType'")
        }
      case ("object", Some(subProperty))        => s"Map[String, ${generateType(subProperty)}]"
      case ("array", Some(subProperty))         => s"Seq[${generateType(subProperty)}]"
      case ("number", None) if format.isDefined =>
        format match {
          case Some("double") => "Double"
          case f              => sys.error(s"Unsupported format '$f' for swaggerType '$swaggerType'")
        }
      case (swaggerType, _) => swaggerType.take(1).toUpperCase + swaggerType.drop(1)
    }

  def sanitizeClassPath(classPath: String): String =
    classPath.replace("#/definitions/", "").replace("-", "") match {
      case "io.k8s.apimachinery.pkg.util.intstr.IntOrString" => s"$existingClassesPrefix.IntOrString"
      case "io.k8s.apiextensionsapiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrBool" =>
        s"$existingClassesPrefix.crd.JSONSchemaPropsOrBool"
      case "io.k8s.apiextensionsapiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrStringArray" =>
        s"$existingClassesPrefix.crd.JSONSchemaPropsOrStringArray"
      case "io.k8s.apiextensionsapiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrArray" =>
        s"$existingClassesPrefix.crd.JSONSchemaPropsOrArray"
      case "io.k8s.apiextensionsapiserver.pkg.apis.apiextensions.v1.JSON" =>
        s"$existingClassesPrefix.crd.JSON"
      case "io.k8s.apimachinery.pkg.runtime.RawExtension" =>
        s"$existingClassesPrefix.crd.RawExtension"
      case c => c
    }
}
