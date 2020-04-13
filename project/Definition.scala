case class Definition(
    description: Option[String],
    required: Option[Seq[String]],
    properties: Option[Map[String, Property]],
    `type`: Option[String],
    `x-kubernetes-group-version-kind`: Option[Seq[Kind]]
)

case class Property(
    description: Option[String],
    format: Option[String],
    `type`: Option[String],
    items: Option[Property],
    additionalProperties: Option[Property],
    $ref: Option[String]
)

case class Kind(
    group: String,
    kind: String,
    version: String
)
