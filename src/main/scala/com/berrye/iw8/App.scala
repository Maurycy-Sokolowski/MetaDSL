package com.berrye.iw8

/**
 *
 * Client side application implementing the iW8 Mobile app for Desktop, Tablets and Mobiles.
 *
 * @author Maurycy Sokolowski
 *
 * @copyright Berrye 2023
 *
 */

import scala.util.{ Try, Success, Failure }
import scala.collection._
import scala.scalajs.js
import js.annotation._
import org.scalajs.dom
import scalatags.JsDom.all._
import scalatags.JsDom._
import js.Dynamic.{ global => g }
import org.scalajs.dom._
import js.annotation.JSExport
import js.UndefOr
import org.scalajs.dom._
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{ FiniteDuration, TimeUnit }
import js.Dynamic.literal
import org.scalajs.jquery._
import scala.scalajs.js.annotation.JSExport
import scala.scalajs.js.Dynamic.{ literal => lit }
import scala.scalajs.js.JSON._
import scala.scalajs.js.URIUtils._
import scalatags.JsDom.all._
import math._
import moment._
import prickle._
import WebParser._
import scala.scalajs.js.annotation.JSName

object iW8 {
	@js.native
	trait JQueryGrid extends JQuery {
		def bootgrid(): Unit
		def bootgrid(o: Unit): Unit
		def modal(s: String): Unit
		def noConflict(): Unit
		def DataTable(): js.Dynamic
		def DataTable(o: js.Object): Unit
		def collapse(): Unit
		def datetimepicker(c: js.Object): Unit
		def datetimepicker(opt: String, value: Boolean): Unit
		def datetimepicker(): Unit
		def selectpicker(): Unit
		def selectpicker(v: String): Unit
		def selectpicker(o: String, v: String): Unit
	}

	@js.native
	@JSGlobal("SignaturePad")
	class SignaturePad(c: org.scalajs.dom.html.Canvas) extends js.Object {
		def toDataURL(): Unit = js.native
		def toDataURL(s: String): Unit = js.native
		def clear(): Unit = js.native
	}

	implicit def JQueryGridOps(jQ: JQuery): JQueryGrid = jQ.asInstanceOf[JQueryGrid]
	lazy val nav = typedTag[dom.html.Div]("nav")
	val $ = js.Dynamic.global.$

	var scriptDef: String = _

	def main(args: Array[String]): Unit = {
		def load(url: String, script: String) {
			scriptDef = script
			if (url.endsWith("dev")) {
				g.document.body.appendChild(div(
					textarea(id := "editcontent", width := "100%", rows := "30", scriptDef),
					button(id := "reload", cls := "btn btn-info", "Load 2.0.17")).render)
				jQuery("#reload").on("click", (e: JQueryEventObject) => {
					scriptDef = jQuery("#editcontent").value().toString
					WebParser.reloadAll(scriptDef)
				})
			} else WebParser.reloadAll(scriptDef)
		}
		Try(load(window.location.href.toLowerCase, store.getItem("script"))).getOrElse(load(window.location.href.toLowerCase, g.staticScript.asInstanceOf[String]))
	}
}
