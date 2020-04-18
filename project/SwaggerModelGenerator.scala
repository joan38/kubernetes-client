import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import sbt.Keys._
import sbt._

object SwaggerModelGenerator extends AutoPlugin {

  object autoImport extends scala.AnyRef {
    lazy val swaggerModel  = taskKey[Unit]("Generate Scala case class for the given Swagger model")
    lazy val swaggerSource = settingKey[File]("Swagger resource files")
  }
  import autoImport._

  override val projectSettings = Seq(
    swaggerModel := swaggerModelTask.value,
    Compile / sourceGenerators += swaggerModelTask.taskValue,
    Compile / swaggerSource := (Compile / sourceDirectory).value / "swagger"
  )

  lazy val swaggerModelTask = Def.task {
    val swaggerFiles =
      Option((Compile / swaggerSource).value.listFiles(FileFilter.globFilter("*.json"))).toSeq.flatten
    swaggerFiles.flatMap(processSwaggerFile(_, (Compile / sourceManaged).value, streams.value.log))
  }

  val skipClasses =
    Set(
      "io.k8s.apimachinery.pkg.apis.meta.v1.WatchEvent",
      "io.k8s.apiextensions-apiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrBool",
      "io.k8s.apiextensions-apiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrStringArray",
      "io.k8s.apiextensions-apiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrArray",
      "io.k8s.apiextensions-apiserver.pkg.apis.apiextensions.v1.JSON"
    )

  val existingClassesPrefix = "com.goyeau.kubernetes.client"

  def classNameFilter(className: String): Boolean = {
    val allowedPrefixes = Seq(
      "io.k8s.api.apps.v1",
      "io.k8s.api.core.v1",
      "io.k8s.api.rbac.v1",
      "io.k8s.api.batch.v1",
      "io.k8s.kubernetes.pkg.apis.policy.v1beta1",
      "io.k8s.api.policy.v1beta1",
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
      "io.k8s.apiextensions-apiserver.pkg.apis.apiextensions.v1.ServiceReference"
    )
    allowedPrefixes.exists(className.startsWith) && !skipClasses.contains(className)
  }

  def processSwaggerFile(swaggerFile: File, outputDir: File, log: Logger): Seq[File] = {
    val json = parse(IO.read(swaggerFile)).fold(throw _, identity)
    for {
      definitionsJson   <- json.hcursor.downField("definitions").focus.toSeq
      definitionsObject <- definitionsJson.asObject.toSeq

      (fullClassName, definition) <- definitionsObject.toMap
      if classNameFilter(fullClassName)
    } yield generateDefinition(fullClassName, definition, outputDir, log)
  }

  def generateDefinition(fullClassName: String, definitionJson: Json, outputDir: File, log: Logger): File = {
    val split       = fullClassName.split("\\.")
    val packageName = sanitizeClassPath(split.init.mkString("."))
    val className   = split.last
    val file        = outputDir / sanitizeClassPath(split.init.mkString("/")) / s"$className.scala"

    val generatedClass = definitionJson.as[Definition].fold(throw _, identity) match {
      case Definition(desc, required, properties, Some("object"), _) =>
        val description = generateDescription(desc)
        val attributes  = generateAttributes(properties.toSeq.flatten.sortBy(_._1), required.toSeq.flatten)
        val caseClass   = s"""import io.circe._
                           |import io.circe.generic.semiauto._
                           |
                           |case class $className(
                           |  ${attributes.replace("\n", "\n  ")}
                           |)
                           |
                           |object $className {
                           |  implicit lazy val encoder: Encoder.AsObject[$className] = deriveEncoder
                           |  implicit lazy val decoder: Decoder[$className] = deriveDecoder
                           |}
                           |""".stripMargin
        s"$description$caseClass"

      case Definition(_, None, None, Some(t), _) =>
        val scalaType = swaggerToScalaType(t)
        s"""import io.circe._
           |
           |case class $className(value: $scalaType) extends AnyVal
           |
           |object $className {
           |  implicit val encoder: Encoder[$className] = obj => Json.from$scalaType(obj.value)
           |  implicit val decoder: Decoder[$className] = _.as[$scalaType].map($className(_))
           |}""".stripMargin

      case d => sys.error(s"Unsupported definition for $fullClassName: $d")
    }

    IO.write(file,
             s"""package $packageName
                |
                |$generatedClass""".stripMargin)
    log.info(s"Generated $file")
    file
  }

  def generateAttributes(properties: Iterable[(String, Property)], required: Seq[String]): String =
    properties.toSeq
      .sortBy {
        case (name, _) =>
          if (required.contains(name)) required.indexOf(name)
          else Int.MaxValue
      }
      .map {
        case (name, property) =>
          val description = generateDescription(property.description)
          val escapedName = escapeAttributeName(name)
          val classPath =
            if (required.contains(name)) generateType(property)
            else s"Option[${generateType(property)}] = None"
          s"""$description$escapedName: $classPath"""
      }
      .mkString(",\n")

  def escapeAttributeName(name: String): String =
    if (name.contains("x-")) s"`$name`"
    else name
      .replace("type", "`type`")
      .replace("class", "`class`")
      .replace("object", "`object`")

  def generateDescription(description: Option[String]): String =
    description.fold("")(d => s"/** ${d.replace("*/", "*&#47;").replace("/*", "&#47;*")} */\n")

  def generateType(property: Property): String =
    (property.`type`, property.$ref) match {
      case (Some(t), None)   => swaggerToScalaType(t, property.items.orElse(property.additionalProperties))
      case (None, Some(ref)) => sanitizeClassPath(ref)
    }

  def swaggerToScalaType(swaggerType: String, subProperty: Option[Property] = None): String =
    (swaggerType, subProperty) match {
      case ("integer", None)             => "Int"
      case ("object", Some(subProperty)) => s"Map[String, ${generateType(subProperty)}]"
      case ("array", Some(subProperty))  => s"Seq[${generateType(subProperty)}]"
      case (swaggerType, _)              => swaggerType.take(1).toUpperCase + swaggerType.drop(1)
    }

  def sanitizeClassPath(classPath: String): String =
    classPath.replace("#/definitions/", "").replace("-", "") match {
      case "io.k8s.apimachinery.pkg.util.intstr.IntOrString" => s"$existingClassesPrefix.IntOrString"
      case "io.k8s.apiextensionsapiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrBool" =>
        s"$existingClassesPrefix.JSONSchemaPropsOrBool"
      case "io.k8s.apiextensionsapiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrStringArray" =>
        s"$existingClassesPrefix.JSONSchemaPropsOrStringArray"
      case "io.k8s.apiextensionsapiserver.pkg.apis.apiextensions.v1.JSONSchemaPropsOrArray" =>
        s"$existingClassesPrefix.JSONSchemaPropsOrArray"
      case "io.k8s.apiextensionsapiserver.pkg.apis.apiextensions.v1.JSON" =>
        s"$existingClassesPrefix.JSON"
      case c => c
    }
}
