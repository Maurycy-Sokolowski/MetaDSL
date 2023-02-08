package com.berrye.iw8
/**
 * Created by sai on 2016/9/6.
 * Modified by Maurycy Sokolowski 2017
 *
 */

import scala.scalajs.js
import js.annotation._
import org.scalajs.dom._
import JSHashes._

object ImplicitHash {

  implicit class HashOps(s: String) {
    def MD5() = new MD5Ops(s)
    def SHA1() = new SHA1Ops(s)
    def SHA256() = new SHA256Ops(s)
    def SHA512() = new SHA512Ops(s)
    def RMD160() = new RMD160Ops(s)
  }

  sealed abstract class JSHashOps(s: String) {
    val m: JSHashes
    def hex = m.hex(s)
    def hex_hmac(key: String) = m.hex_hmac(key, s)
    def b64: String = m.b64(s)
    def b64_hmac(key: String) = m.b64_hmac(key, s)
    def any(encoding: String) = m.any(s, encoding)
    def any_hmac(key: String, encoding: String) = m.any_hmac(key, s, encoding)
    def raw(s: String): String = m.raw(s)
    def setUpperCase(a: Boolean) = m.setUpperCase(a)
    def setPad(a: String) = m.setPad(a)
    def setUTF8(a: Boolean) = m.setUTF8(a)
  }

  implicit class MD5Ops(s: String) extends JSHashOps(s) {
    val m = new MD5()
  }

  implicit class SHA1Ops(s: String) extends JSHashOps(s) {
    val m = new SHA1()
  }

  implicit class SHA256Ops(s: String) extends JSHashOps(s) {
    val m = new SHA256()
  }

  implicit class SHA512Ops(s: String) extends JSHashOps(s) {
    val m = new SHA512()
  }

  implicit class RMD160Ops(s: String) extends JSHashOps(s) {
    val m = new RMD160()
  }
}


@JSGlobal("Hashes.JSHashes")
@js.native
abstract class JSHashes(options: HashesOptions) extends js.Object {
  def hex(s: String): String = js.native
  def hex_hmac(key: String, string: String): String = js.native
  def b64(s: String): String = js.native
  def b64_hmac(key: String, string: String): String = js.native
  def any(s: String, encoding: String): String = js.native
  def any_hmac(key: String, string: String, encoding: String): String = js.native
  def raw(s: String): String = js.native
  def setUpperCase(a: Boolean): String = js.native
  def setPad(a: String): String = js.native
  def setUTF8(a: Boolean): String = js.native

  def vm_test(): Unit = js.native
}

object JSHashes {
  val defaultOptions = HashesOptions(uppercase = false, "=", utf8 = true)
}

@JSGlobal("Hashes.MD5")
@js.native
class MD5(options: HashesOptions = defaultOptions) extends JSHashes(options)

@JSGlobal("Hashes.SHA1")
@js.native
class SHA1(options: HashesOptions = defaultOptions) extends JSHashes(options)

@JSGlobal("Hashes.SHA256")
@js.native
class SHA256(options: HashesOptions = defaultOptions) extends JSHashes(options)

@JSGlobal("Hashes.SHA512")
@js.native
class SHA512(options: HashesOptions = defaultOptions) extends JSHashes(options)

@JSGlobal("Hashes.RMD160")
@js.native
class RMD160(options: HashesOptions = defaultOptions) extends JSHashes(options)

@JSGlobal("Hashes.HashesOptions")
@js.native
class HashesOptions(
  uppercase: Boolean,
  pad: String,
  utf8: Boolean) extends js.Object

object HashesOptions {
  def apply(
    uppercase: Boolean,
    pad: String,
    utf8: Boolean): HashesOptions = new HashesOptions(uppercase, pad, utf8)
}
