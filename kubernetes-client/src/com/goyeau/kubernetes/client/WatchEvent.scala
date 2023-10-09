package com.goyeau.kubernetes.client

/** Event represents a single event to a watched resource. */
import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.syntax.*

sealed trait EventType

object EventType {
  case object ADDED    extends EventType
  case object DELETED  extends EventType
  case object MODIFIED extends EventType
  case object ERROR    extends EventType

  implicit val encodeEventType: Encoder[EventType] = {
    case ADDED    => "ADDED".asJson
    case DELETED  => "DELETED".asJson
    case MODIFIED => "MODIFIED".asJson
    case ERROR    => "ERROR".asJson
  }

  implicit val decodeEventType: Decoder[EventType] = Decoder.decodeString.emap {
    case "ADDED"    => Right(ADDED)
    case "DELETED"  => Right(DELETED)
    case "MODIFIED" => Right(MODIFIED)
    case "ERROR"    => Right(ERROR)
  }
}

case class WatchEvent[T](
    `type`: EventType,
    /* Object is:
     * If Type is Added or Modified: the new state of the object.
     * If Type is Deleted: the state of the object immediately before deletion.
     * If Type is Error: *Status is recommended; other types may make sense depending on context.
     */
    `object`: T
)

object WatchEvent {
  implicit def encoder[T: Encoder]: Encoder.AsObject[WatchEvent[T]] = deriveEncoder[WatchEvent[T]]
  implicit def decoder[T: Decoder]: Decoder[WatchEvent[T]]          = deriveDecoder[WatchEvent[T]]
}
