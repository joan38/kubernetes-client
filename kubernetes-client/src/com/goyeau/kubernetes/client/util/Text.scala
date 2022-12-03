package com.goyeau.kubernetes.client.util

import cats.effect.Sync
import cats.syntax.all.*
import fs2.io.file.{Files, Path}

object Text {

  def readFile[F[_]: Sync: Files](path: Path): F[String] =
    Files[F].readAll(path).through(fs2.text.utf8.decode).compile.toList.map(_.mkString)

}
