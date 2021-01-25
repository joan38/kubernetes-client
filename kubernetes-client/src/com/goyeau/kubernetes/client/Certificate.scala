package com.goyeau.kubernetes.client

import java.io.{ByteArrayInputStream, File, FileInputStream, InputStream}
import java.util.Base64

sealed trait Certificate {
  val bytesStream: InputStream // TODO: effect should be returned here. For compatibility reasons I leave it as it is, but in the future it might be worth refactoring
}

object Certificate {
  final case class CertData(data: String) extends Certificate {
    override val bytesStream = new ByteArrayInputStream(Base64.getDecoder.decode(data))
  }
  final case class CertFile(file: File) extends Certificate {
    override val bytesStream = new FileInputStream(file)
  }
}
