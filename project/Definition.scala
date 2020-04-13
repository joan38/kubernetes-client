case class Definition(
    description: Option[String],
    required: Option[Seq[String]],
    properties: Option[Map[String, Property]],
    `type`: Option[String]
)

case class Property(
    description: Option[String],
    `type`: Option[String],
    items: Option[Property],
    additionalProperties: Option[Property],
    $ref: Option[String]
)
