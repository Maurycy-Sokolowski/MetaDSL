package com.berrye.iw8


/**
  * UUID generation.
  *
 * @author Maurycy Sokolowski
 *
 * @copyright Berrye 2023
  *
  */
object UUID {
  val CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray

  /**
    * Generate a random uuid of the specified length. Example: uuid(15) returns
    * "VcydxgltxrVZSTV"
    *
    * @param len
    *            the desired number of characters
    */
  def uuid(len: Int): String = uuid(len, CHARS.length)

  /**
    * Generate a random uuid of the specified length, and radix. Examples:
    * <ul>
    * <li>uuid(8, 2) returns "01001010" (8 character ID, base=2)
    * <li>uuid(8, 10) returns "47473046" (8 character ID, base=10)
    * <li>uuid(8, 16) returns "098F4D35" (8 character ID, base=16)
    * </ul>
    *
    * @param len
    *            the desired number of characters
    * @param radix
    *            the number of allowable values for each character (must be <=
    *            62)
    */
  def uuid(len: Int, radix: Int) = {
    if (radix > CHARS.length) {
      throw new IllegalArgumentException
    }
    val uuid = Array.ofDim[Char](len)
    for (i <- 0 until len) {
      uuid(i) = CHARS((Math.random() * radix).toInt)
    }
    new String(uuid)
  }

  /**
    * Generate a RFC4122, version 4 ID. Example:
    * "92329D39-6F5C-4520-ABFC-AAB64544E172"
    */
  def uuid = {
    val uuid = Array.ofDim[Char](36)
    // rfc4122 requires these characters
    uuid(8) = '-'
    uuid(13) = '-'
    uuid(18) = '-'
    uuid(23) = '-'
    uuid(14) = '4'
    for (i <- 0.until(36) if uuid(i) == 0) {
      val r = (Math.random() * 16).toInt
      uuid(i) = CHARS(if ((i == 19)) (r & 0x3) | 0x8 else r & 0xf)
    }
    new String(uuid)
  }
}
