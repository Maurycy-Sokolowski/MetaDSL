package com.berrye.iw8

import scala.scalajs.js
import org.scalajs.dom.raw.WebSocket
import org.scalajs.dom
import org.scalajs.dom._
import scala.scalajs.js.timers._

object BinaryType extends Enumeration {
  type BinaryType = Value
  val blob, arraybuffer = Value
}
import BinaryType._
import scala.scalajs.js.typedarray.ArrayBuffer

case object ConnectingEvent

/**
 * A translation to scala from
 * 	https://github.com/joewalnes/reconnecting-websocket
 * which is why it's absolutely so un-scala like
 *
 * This behaves like a WebSocket in every way, except if it fails to connect,
 * or it gets disconnected, it will repeatedly poll until it successfully connects
 * again.
 *
 * It is API compatible, so when you have:
 *   ws = new WebSocket("ws://....")
 * you can replace with:
 *   ws = new ReconnectingWebSocket("ws://....")
 *
 * It is API compatible with the standard WebSocket API, apart from the following members:
 *
 * - `bufferedAmount`
 * - `extensions`
 *
 * @param url - The url you are connecting to.
 *
 * @param protocol - Optional string or array of protocols.
 *
 * @param debug - Whether this instance should log debug messages. Default: false.
 *
 * @param automaticOpen - Whether or not the websocket should attempt to connect immediately upon instantiation. The socket can be manually opened or closed at any time using ws.open() and ws.close().
 *
 * @param reconnectInterval The number of milliseconds to delay before attempting to reconnect. Default: 1000.
 *
 * @param maxReconnectInterval The maximum number of milliseconds to delay a reconnection attempt. Default: 60000.
 *
 * @param minReconnectInterval The minimum number of milliseconds to delay a reconnection attempt. Default: 200.
 *
 * @param reconnectDecay The rate of increase of the reconnect delay. Allows reconnect attempts to back off when problems persist. Default: 1.5.
 *
 * @param timeoutInterval The maximum time in milliseconds to wait for a connection to succeed before closing and retrying. Default: 2000.
 *
 */
class ReconnectingWebsocket(
  url: => String,
  protocol: Option[String] = None,
  debug: Boolean = false,
  automaticOpen: Boolean = true,
  reconnectInterval: Int = 1000,
  maxReconnectInterval: Long = 60000,
  reconnectDecay: Double = 1.5,
  timeoutInterval: Long = 2000,
  minReconnectInterval: Long = 200,
  maxReconnectAttempts: Option[Int] = None,
  binaryType: BinaryType = blob,
  onOpen: Event => Unit = { event => },
  onClose: CloseEvent => Unit = { event => },
  onMessage: MessageEvent => Unit = { event => },
  onConnecting: () => Unit = { () => },
  onError: ErrorEvent => Unit = { event => }) {

  /////////////////////////////////////////////////////////////////////////////////
  // I removed some of the vars, but it's not always that simple! It's so much easier
  // when you start with scala
  private var forcedClose = false
  private var reconnectAttempt: Int = 0
  private var ws: Option[WebSocket] = automaticOpen match {
    case true =>
      Option(open(false))
    case false =>
      None
  }

  def open(): Unit = open(false)

  private def open(isReconnect: Boolean): WebSocket = {
    val localWS = new WebSocket(url)
    localWS.binaryType = binaryType.toString

    if (maxReconnectAttempts.isEmpty || reconnectAttempt <= maxReconnectAttempts.get) {
      if (!isReconnect) {
        //this is not a reconnect, so we're good.
        reconnectAttempt = 0
      }
      var timedOut = false
      onConnecting()
      if (debug) {
        println(s"ReconnectingWebsocket: attempt-connect (attempt #${reconnectAttempt})")
      }
      val timeout = setTimeout(timeoutInterval) {
        if (debug) {
          println("ReconnectingWebsocket: connection timeout")
        }
        timedOut = true
        localWS.close()
        timedOut = false
      }
      localWS.onopen = {
        (event: Event) =>
          clearTimeout(timeout)
          if (debug) {
            println(s"ReconnectingWebsocket: onopen: ${event}")
          }
          //Reset the reconnect attempts, since open was successful
          reconnectAttempt = 0
          onOpen(event)
      }
      localWS.onclose = {
        (event: CloseEvent) =>
          clearTimeout(timeout)
          if (debug) {
            println(s"ReconnectingWebsocket: onclose: ${event.code}-${event.reason}")
          }
          ws = None
          if (!forcedClose) {
            onConnecting()
            if (reconnectAttempt > 0 && !timedOut) {
              onClose(event)
            }
          }

          import Math._
          //Per the RFC, wait a random time before retrying to connect
          //This algorithm for figuring out the reconnect time is called "binary exponential backoff"
          val apow = pow(reconnectDecay, reconnectAttempt)
          val minlong = min(Long.MaxValue, apow)
          val rand = random() * minlong
          val interval = minReconnectInterval + (reconnectInterval * rand)
          val timeoutInterval = min(maxReconnectInterval, interval)
          if (debug) {
            println(s"ReconnectingWebsocket: attempting reconnect #${reconnectAttempt}  in ${timeoutInterval} ms")
          }
          setTimeout(timeoutInterval) {
            reconnectAttempt = reconnectAttempt + 1
            ws = Option(open(true))
          }
      }
      localWS.onmessage = {
        (event: MessageEvent) =>
          if (debug) {
            println(s"ReconnectingWebsocket: onmessage: ${event.data.toString}")
          }
          onMessage(event)
      }
      /*localWS.onerror = {
        (event: ErrorEvent) =>
          if (debug) {
            println(s"ReconnectingWebsocket: onerror: ${event}")
          }
          onError(event)
      }*/
    }
    localWS
  }

  /**
   * Transmits data to the server over the WebSocket connection.
   *
   * @param data a text string, ArrayBuffer or Blob to send to the server.
   */
  def send(data: String): Unit = {
    ws match {
      case None =>
        throw new Error("INVALID STATE: pausing to reconnect websocket")
      case Some(ws) =>
        if (debug) {
          println(s"ReconnectingWebsocket: send: ${data}")
        }
        ws.send(data)
    }
  }
  /**
   * Transmits data to the server over the WebSocket connection.
   *
   * @param data a text string, ArrayBuffer or Blob to send to the server.
   */
  def send(data: Blob): Unit = {
    ws match {
      case None =>
        throw new Error("INVALID STATE: pausing to reconnect websocket")
      case Some(ws) =>
        if (debug) {
          println(s"ReconnectingWebsocket: send: ${data}")
        }
        ws.send(data)
    }
  }
  /**
   * Transmits data to the server over the WebSocket connection.
   *
   * @param data a text string, ArrayBuffer or Blob to send to the server.
   */
  def send(data: ArrayBuffer): Unit = {
    ws match {
      case None =>
        throw new Error("INVALID STATE: pausing to reconnect websocket")
      case Some(ws) =>
        if (debug) {
          println(s"ReconnectingWebsocket: send: ${data}")
        }
        ws.send(data)
    }
  }

  /**
   * Closes the WebSocket connection or connection attempt, if any.
   * If the connection is already CLOSED, this method does nothing.
   */
  def close(code: Int = 1000, reason: String = "Unknown reason") = {
    forcedClose = true
    ws match {
      case None =>
        if (debug) {
          println(s"ReconnectingWebsocket: already closed")
        }
      case Some(ws) =>
        if (debug) {
          println(s"ReconnectingWebsocket: forcing close")
        }
        ws.close(code, reason)
    }
  }
  def refresh() = {
    ws match {
      case None =>
        if (debug) {
          println(s"ReconnectingWebsocket: Refresh, but ws == None already")
        }
      case Some(ws) =>
        if (debug) {
          println(s"ReconnectingWebsocket: Additional public API method to refresh the connection if still open (close, re-open). For example, if the app suspects bad data / missed heart beats, it can try to refresh.")
        }
        ws.close()
    }
  }

}