import io.circe._
import io.circe.parser._
import io.circe.generic.auto._
import sbt.Keys._
import sbt._

object SwaggerModelGenerator extends AutoPlugin {

  object autoImport extends scala.AnyRef {
    lazy val swaggerModel = taskKey[Unit]("Generate Scala case class for the given Swagger model")
    lazy val swaggerSource = settingKey[File]("Swagger resource files")
  }
  import autoImport._

  override val projectSettings = Seq(
    swaggerModel := swaggerModelTask.value,
    sourceGenerators in Compile += swaggerModelTask.taskValue,
    swaggerSource in Compile := (sourceDirectory in Compile).value / "swagger"
  )

  lazy val swaggerModelTask = Def.task {
    val swaggerFiles =
      Option((swaggerSource in Compile).value.listFiles(FileFilter.globFilter("*.json"))).toSeq.flatten
    swaggerFiles.flatMap(processSwaggerFile(_, (sourceManaged in Compile).value, streams.value.log))
  }

  def processSwaggerFile(swaggerFile: File, outputDir: File, log: Logger) = {
    val json = parse(IO.read(swaggerFile)).fold(throw _, identity)
    for {
      definitionsJson <- json.hcursor.downField("definitions").focus.toSeq
      definitionsObject <- definitionsJson.asObject.toSeq
      classFile <- definitionsObject.toMap.map {
        case (fullClassName, definition) => generateDefinition(fullClassName, definition, outputDir, log)
      }
    } yield classFile
  }

  def generateDefinition(fullClassName: String, definitionJson: Json, outputDir: File, log: Logger) = {
    val split = fullClassName.split("\\.")
    val packageName = sanitizeClassPath(split.init.mkString("."))
    val className = split.last
    val file = outputDir / sanitizeClassPath(split.init.mkString("/")) / s"$className.scala"

    val generatedClass = definitionJson.as[Definition].fold(throw _, identity) match {
      case Definition(desc, required, properties, None) =>
        val description = generateDescription(desc)
        val attributes = generateAttributes(properties.toSeq.flatten.sortBy(_._1), required.toSeq.flatten)
        val caseClass = s"""case class $className(
                           |  ${attributes.replace("\n", "\n  ")}
                           |)""".stripMargin
        s"$description$caseClass"
      case Definition(None, None, None, Some(t)) =>
        val scalaType = swaggerToScalaType(t)
        s"""import io.circe._
           |
           |case class $className(value: $scalaType) extends AnyVal
           |
           |object $className {
           |  implicit val encode: Encoder[$className] = obj => Json.from$scalaType(obj.value)
           |  implicit val decode: Decoder[$className] = _.as[$scalaType].map($className(_))
           |}""".stripMargin
    }
    IO.write(file, s"""package $packageName
                      |
                      |$generatedClass""".stripMargin)
    log.info(s"Generated $file")
    file
  }

  def generateAttributes(properties: Iterable[(String, Property)], required: Seq[String]) =
    properties.toSeq
      .sortBy {
        case (name, _) =>
          if (required.contains(name)) required.indexOf(name)
          else Int.MaxValue
      }
      .map {
        case (name, property) =>
          val description = generateDescription(property.description)
          val escapedName = name.replace("type", "`type`").replace("class", "`class`").replace("object", "`object`")
          val classPath =
            if (required.contains(name)) generateType(property)
            else s"Option[${generateType(property)}] = None"
          s"""$description$escapedName: $classPath"""
      }
      .mkString(",\n")

  def generateDescription(description: Option[String]) =
    description.fold("")(d => s"/** ${d.replace("*/", "*&#47;").replace("/*", "&#47;*")} */\n")

  def generateType(property: Property): String =
    (property.`type`, property.$ref) match {
      case (Some(t), None)   => swaggerToScalaType(t, property.items.orElse(property.additionalProperties))
      case (None, Some(ref)) => sanitizeClassPath(ref)
    }

  def swaggerToScalaType(swaggerType: String, subProperty: Option[Property] = None) =
    (swaggerType, subProperty) match {
      case ("integer", None)             => "Int"
      case ("object", Some(subProperty)) => s"Map[String, ${generateType(subProperty)}]"
      case ("array", Some(subProperty))  => s"Seq[${generateType(subProperty)}]"
      case (swaggerType, _)              => swaggerType.take(1).toUpperCase + swaggerType.drop(1)
    }

  def sanitizeClassPath(classPath: String) =
    classPath.replace("#/definitions/", "").replace("-", "") match {
      case "io.k8s.apimachinery.pkg.util.intstr.IntOrString" => "com.goyeau.kubernetesclient.IntOrString"
      case c                                                 => c
    }
}
