package com.berrye.iw8

import scala.language.postfixOps
import scala.annotation.tailrec
import scala.util.parsing.combinator._
import scala.util.parsing.input._
import scala.util.{ Try, Success, Failure }
import scala.collection.mutable.ListBuffer
import scalatags._
import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.jquery._
import org.scalajs.jquery.{jQuery => JQ, JQueryAjaxSettings, JQueryXHR}
import scalatags.JsDom.all._
import scalatags.JsDom._
import org.scalajs.dom.raw._
import org.scalajs.dom._
import org.scalajs.dom.html
import js.Dynamic.{ global => g }
import js.annotation.JSImport
import js.annotation.JSExport
import js.annotation.JSName
import js.isUndefined
import js.URIUtils._
import js.Dynamic.{ literal => lit }
import js.JSON
import js.JSON._
import js.timers._
import js.JSConverters._
import scala.reflect._
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{ FiniteDuration, TimeUnit }
import scala.concurrent.ExecutionContext.Implicits.global
import js.Dynamic.literal
import math._
import moment._
import prickle._
import UUID._
import iW8._
import Charts._
import ImplicitHash.SHA256Ops

object WebParser extends JavaTokenParsers with Positional {
    val store = window.localStorage
    var scriptVal: Option[ScriptCls] = None
    var name = ""
    var pages = Map[String, () => JsDom.TypedTag[org.scalajs.dom.raw.Element]]()
    var sigs = Map[String, SignaturePad]()
    var dropdownsLoad = Map[String, Formats]()
    var dropdowns = Map[String, Map[Int, String]]()
    var menu = List[(String, String, Option[List[String]])]()
    var submenu = List[String]()
    var currentElement = ""
    var menus = ("", "")
    var selections = Map[String,String]()
    var timer: SetTimeoutHandle = _
    var mobileSite = false
    var mobileContext = Map[String,Any]()

  case class Context(element: String = "", tr: Transition = Transition("", None), response: js.Dynamic = js.Object.asInstanceOf[js.Dynamic])
  var transitions = List[Context]()
  var currentContext: Option[Context] = None

  def website = "site" ~> title <~ "with" ~ block

  def title = stringToken ~ ("mobile"?) ~ ("dark"?) ^^ {
    case s ~ mobile ~ dark => {
      jQuery("#title").text(s)
      name = s
      mobileSite = mobile.isDefined
      if (dark.isDefined) jQuery("#theme").attr("href", "https://unpkg.com/onsenui/css/dark-onsen-css-components.css")
    }
  }

  def block = "{" ~> rep(pageSite) <~ "}"

  def processError(jqXHR: JQueryXHR, textStatus: js.Any, errorThrow: js.Any) {
    	val textXHR = stringify(jqXHR)
    	val textError = stringify(textStatus)
      println(s"jqXHR=$textXHR,text=$textError,err=$errorThrow")
      Try(if (textXHR.contains("502")) click("networkerror") else if (textXHR.contains("400") || textXHR.contains("401")) {
        jQuery("#genericerrormsg").text(jqXHR.asInstanceOf[js.Dynamic].responseJSON.errorReason.asInstanceOf[String])
        click("genericerror")
      } else click("dataerror"))
  }

  def processQueryStr(queryStr: String, useBody: Boolean) = {
    val split = math.max(queryStr.lastIndexOf("?"), queryStr.lastIndexOf("{"))
    val factor = if (split > 0 && queryStr.charAt(split) == '?') 1 else 0
    if (useBody && split > 0) (queryStr.take(split), queryStr.drop(split + factor)) else (queryStr, "")
  }

  def queryServer(queryStr: String, proc: (js.Any) => Unit, typeQuery: String = "GET", useBody: Boolean = false) = if (queryStr.contains("mockup")) {
	  println("Using mockup data for a call")
	  proc(Seq(1 to 10).toJSArray)
  } else {
    var pidx = 0
    def progress(idd: String) = if (!"navigationprogress".notPresent && idd.notPresent) jQuery("#navigationprogress").append(div(id := idd,
        div(cls := "spinner-grow", style := js.Dictionary("width" -> "8rem", "height" -> "8rem"), role := "status")).render)
    if (pidx == 0) {
        progress("progressshow")
        jQuery("#progressshow").show()
    }
    pidx = pidx + 1
    def delprog {
      pidx = pidx - 1
      if (pidx == 0) jQuery("#progressshow").hide()
    }
    val (qs, dd) = processQueryStr(queryStr, useBody)
    println("Original is " + queryStr + ", body is " + useBody + ", values: " + qs + ", " + dd)
    jQuery.ajax(js.Dynamic.literal(
      url = qs,
      contentType = "text/plain",
      data = decodeURI(dd),
      success = { (data: js.Any, textStatus: js.Any, jqXHR: JQueryXHR) =>
        delprog
        proc(data)
      },
      error = { (jqXHR: JQueryXHR, textStatus: js.Any, errorThrow: js.Any) =>
        delprog
        processError(jqXHR, textStatus, errorThrow)
      },
      `type` = typeQuery,
      timeout = 300000
    ).asInstanceOf[JQueryAjaxSettings])
  }

  def dialog(idd: String, title: String, msg: String, large: Boolean = false) = if (idd.notPresent) g.document.body.appendChild(div(cls := "modal fade modal-mini modal-primary", id := idd, tabindex := "-1", role := "dialog", attr("aria-labelledby") := title, attr("aria-hidden") := "true",
    div(
      cls := "modal-dialog" + (if (large) " modal-lg" else ""),
      div(
        cls := "modal-content",
        div(
          cls := "modal-header justify-content-center",
          div(
            cls := "modal-profile",
            i(cls := "now-ui-icons users_circle-08"))),
        div(
          cls := "modal-body",
          p(id := idd + "msg", msg)),
        div(
          cls := "modal-footer",
          button(`type` := "button", cls := "btn btn-link btn-neutral", ""),
          button(`type` := "button", cls := "btn btn-link btn-neutral", attr("data-dismiss") := "modal", "Close"))))).render)

  def click(idd:String) {
  	if ((idd + "trigger").notPresent) g.document.body.appendChild(button(cls := "btn", id := idd + "trigger", attr("data-toggle") := "modal", attr("data-target") := "#" + idd).render)
  	jQuery("#" + idd + "trigger").click()
  	jQuery("#" + idd + "trigger").remove()
  }

  def merge[A, B](a: Map[A, B], b: Map[A, B])(mergef: (B, Option[B]) => B): Map[A, B] = {
	  val (big, small) = if (a.size > b.size) (a, b) else (b, a)
	  small.foldLeft(big) { case (z, (k, v)) => z + (k -> mergef(v, z.get(k))) }
	}
	
	def mergeIntSum[A](a: Map[A, Int], b: Map[A, Int]): Map[A, Int] = merge(a, b)((v1, v2) => v2.map(_ + v1).getOrElse(v1))

  def extractMapOrUser(s: String) = if (selections.contains(s)) selections(s) else currentContext.get.response.extractV(s).asInstanceOf[String]

  def queryFormat(v: Query, tail: String = "") = if (v.url.contains("mockup")) v.url else {
    val rests = Try(v.extras.get.filter(e => e.typ.equals("rest"))).getOrElse(List()).map(e => e.value.get)
    val restStr = rests.foldLeft(List[String]())((l, s) => l ++ List(Try(extractMapOrUser(s)).getOrElse(s))).mkString("/")
    val parms = Try(v.extras.get.filter(e => e.typ.equals("param"))).getOrElse(List()).map(e => Try(Some(e.value.get)).getOrElse(None)).flatten
    val parmStr = parms.foldLeft(List[String]())((l, s) => l ++ List(Try(extractMapOrUser(s)).getOrElse(s))).mkString("&")
    val objs = Try(v.extras.get.filter(e => e.typ.equals("object"))).getOrElse(List()).map(e => Try(Some(e.params.get)).getOrElse(None)).flatten
    val objS = if (objs.size > 0) Some(objs.map(v => {
      val mm = v.map(v => (v.field, if (v.fixed) {
        if (v.inputField.toLowerCase.equals("true")) true else if (v.inputField.toLowerCase.equals("false")) false else Try(v.inputField.toDouble).getOrElse(v.inputField)
      } else if (v.sha256) Try(currentContext.get.response.extractV(v.inputField).asInstanceOf[String].hex).getOrElse("") else Try(currentContext.get.response.extractV(v.inputField).asInstanceOf[String].toDouble).getOrElse(Try(currentContext.get.response.extractV(v.inputField).asInstanceOf[String]).getOrElse("")))).toMap
      mm.toJSDictionary.asInstanceOf[js.Dynamic]
    })) else None
    val curl = v.url + restStr
    (curl + (if (curl.indexOf("?") == -1) "?" else "") + parmStr + (if (!objS.isEmpty) encodeURI(stringify(objS.get(0))) else if (!tail.isEmpty) ((if (!parmStr.isEmpty) "&" else "") + tail) else "")).replaceAll("=&", "=")
  }
 
  val autoplay = attr("autoplay")
  val autoscroll = attr("auto-scroll")
  val fullscreen = attr("fullscreen")
  val icon = attr("icon")
  val labelOns = attr("label")
  val overscrollable = attr("overscrollable")
  val page = attr("page")
  val side = attr("side")
  val swipeable = attr("swipeable")
  val tappable = attr("tappable")

  val collapse = attr("collapse")
  val modifier = attr("modifier")
  val onsButton = tag("ons-button")
  val onsCarousel = tag("ons-carousel")
  val onsCarouselItem = tag("ons-carousel-item")
  val onsDialog = tag("ons-dialog")
  val onsIcon = tag("ons-icon")
  val onsInput = tag("ons-input")
  val onsList = tag("ons-list")
  val onsListItem = tag("ons-list-item")
  val onsListHeader = tag("ons-list-header")
  val onsPage = tag("ons-page")
  val onsCheckbox = tag("ons-checkbox")
  val onsScroller = tag("ons-scroller")
  val onsSearchInput = tag("ons-search-input")
  val onsSplitter = tag("ons-splitter")
  val onsSplitterContent = tag("ons-splitter-content")
  val onsSplitterSide = tag("ons-splitter-side")
  val onsSwitch = tag("ons-switch")
  val onsTab = tag("ons-tab")
  val onsTabbar = tag("ons-tabbar")
  val onsToolbar = tag("ons-toolbar")
  val onsToolbarButton = tag("ons-toolbar-button")
  val onsTemplate = tag("ons-template")
  val video = tag("video")

    var wsconn: Option[WebSocket] = None

  case class StaticEntry(value: String, typ: String, back: Boolean, ws: Option[String], sp: Option[StaticStruct])
  def staticEntry = stringToken ~ (("button" | "checkbox" | "text" | "signature")?) ~ (("back")?) ~ (("ws" ~> stringToken)?) ~ ((staticStruct)?) ^^ {
    case s ~ t ~ b ~ ws ~ p => StaticEntry(s, Try(t.get).getOrElse("button"), b.isDefined, ws, p)
  }

  case class StaticStruct(l: List[StaticEntry])
  def staticStruct: Parser[StaticStruct] = "static" ~> "{" ~> rep(staticEntry) <~ "}" ^^ {
    case l => StaticStruct(l)
  }

 
    def getObject(key: String) = Try(JSON.parse(store.getItem(key).asInstanceOf[String]).asInstanceOf[js.Dictionary[js.Dynamic]]).getOrElse(Map().asInstanceOf[js.Dictionary[js.Dynamic]])

    def updateMap(m: Map[String, Any], hier: Seq[String], v: Any): Map[String, Any] = hier.toList match {
        case Nil => m
        case List(h) => m + (h -> v)
        case h :: t => m + (h -> updateMap(if (m.contains(h)) m(h).asInstanceOf[js.Dictionary[js.Dynamic]].toMap else Map[String, Any](), t, v).toJSDictionary.asInstanceOf[js.Dictionary[js.Dynamic]])
    }

    def updateObject(key: String, hier: Seq[String], v: Any): Unit = {
        val mm = updateMap(Try(getObject(key).toMap).getOrElse(Map()), hier, v)
        val mmid = if (!mm.contains("ID")) mm ++ Map("id" -> UUID.uuid) else mm
        val obj = mmid.toJSDictionary.asInstanceOf[js.Dynamic]
        store.setItem(key, stringify(obj))
    }

def pageSite = "add" ~> stringToken ~ (("title" ~> stringToken)?) ~ (("message" ~> stringToken)?) ~ (("lines" ~> "{" ~> rep(stringToken) <~ "}")?) ~ (("footer" ~> stringToken)?) ~ ("dashboard" | "dasheader" | "login" | "main" | "menu" | "table" | "page") ~ (("items" ~> wholeNumber)?) ~ (wholeNumber?) ~ (header?) ~ ("show"?) ~ (("color" ~> stringToken)?) ~ (("background" ~> stringToken)?) ~ (("script" ~> scriptTok)?) ~ (("with" ~> "{" ~> rep(elements) <~ "}")?) ~ (("display" ~> "{" ~> query <~ "}")?) ~ (rep(panel)?) ~ (rep(go)?) ~ (("widgets" ~> "{" ~> rep(widgets) <~ "}")?) ~ (("entries" ~> "{" ~> rep(reports) <~ "}")?) ~ ((staticStruct)?) ^^ {
    case s ~ title ~ message ~ lines ~ footer ~ t ~ itemsNum ~ days ~ header ~ sh ~ mainColor ~ back ~ scriptEl ~ elems ~ disp ~ pl ~ trans ~ widg ~ men ~ static => {
      println("adding " + s + ", " + t)
      val mainRef = s.asId + UUID.uuid.replaceAll("-","")
      currentElement = s
      t match {
        case "dashboard" => {
          menu = menu ++ List((s, t, None))
          pages += s -> (() => {
            searchablePanel = None
            if (header.isDefined) {
				println("Dashboard: " + header.get.query.url + ", " + header.get.query.url.contains("mockup"))
                queryServer(queryFormat(header.get.query, "days=365"), (data: js.Any) => {
                    val l = data.asInstanceOf[js.Array[js.Dynamic]].toList
					println("Data is " + l.mkString(","))
                    val mth = Map(0 -> "JAN", 1 -> "FEB", 2 -> "MAR", 3 -> "APR", 4 -> "MAY", 5 -> "JUN", 6 -> "JUL", 7 -> "AUG", 8 -> "SEP", 9 -> "OCT", 10 ->"NOV", 11 -> "DEC")
                    val cm = Moment().month().asInstanceOf[Int]
                    def hier(i: Int, f: Int) = if (i < f) i else i - 12
                    val pr = l.foldLeft(Map[Int, Int]())((m, v) => if (header.get.query.url.contains("mockup")) {
						println("Dashboard mockup data")
						Map(0 -> 8, 1 -> 3, 2 -> 6, 3 -> 2, 4 -> 3, 5 -> 6, 6 -> 2, 7 -> 3, 8 -> 6, 9 -> 2, 10 -> 3, 11 -> 6)
					} else {
                        val dt = Try(Moment(v.extractV(header.get.timeField.get).asInstanceOf[Int])).getOrElse(Moment(v.recordCreated.asInstanceOf[String])).month().asInstanceOf[Int]
                        mergeIntSum(m, Map(dt -> 1))
					}).toSeq.sortWith((v1, v2) => hier(v1._1,cm) < hier(v2._1, cm)).map(e => (mth(e._1), e._2))
                println("Got " + l.size + " elements and dashboard is " + pr)
	              get[html.Canvas](mainRef + "header").fold(
	                errorMsg => println("Could not find canvas. Error is {}", errorMsg),
	                canvas => getContext2D(canvas).fold(
	                  errorMsg => println("Couldn't get rendering context of canvas: {}. Error: {}", canvas, errorMsg),
	                  context => {
	                    new JSChart(context, js.Dynamic.literal(
	                      `type`= "line",
	                      data = js.Dynamic.literal(
	                        labels = pr.map(e => e._1).toJSArray,
	                        datasets = Seq(js.Dynamic.literal(
	                          label = header.get.title,
	                          borderColor = "#F00",
	                          pointBorderColor = "#F00",
	                          pointBackgroundColor = "#1e3d60",
	                          pointHoverBackgroundColor = "#1e3d60",
	                          pointHoverBorderColor = "#F00",
	                          pointBorderWidth = 1,
	                          pointHoverRadius = 7,
	                          pointHoverBorderWidth = 2,
	                          pointRadius = 5,
	                          fill = true,
	                          backgroundColor = "#444",
	                          borderWidth = 2,
	                          data = pr.map(e => e._2).toJSArray
	                        )).toJSArray
	                      ),
	                      options = js.Dynamic.literal(
	                        layout = js.Dynamic.literal(
	                          padding = js.Dynamic.literal(
	                            left = 20,
	                            right= 20,
	                            top= 0,
	                            bottom= 0
	                          )
	                        ),
	                        maintainAspectRatio= false,
	                        tooltips= js.Dynamic.literal(
	                          backgroundColor= "#fff",
	                          titleFontColor= "#333",
	                          bodyFontColor= "#666",
	                          bodySpacing= 4,
	                          xPadding= 12,
	                          mode= "nearest",
	                          intersect= 0,
	                          position= "nearest"
	                        ),
	                        legend = js.Dynamic.literal(
	                          position= "bottom",
	                          fillStyle= "#FFF",
	                          display= false
	                        ),
	                        scales = js.Dynamic.literal(
	                          yAxes= Seq(js.Dynamic.literal(
	                            ticks= js.Dynamic.literal(
	                              fontColor= "rgba(255,255,255,0.4)",
	                              fontStyle= "bold",
	                              beginAtZero= true,
	                              maxTicksLimit= 5,
	                              padding= 10
	                            ),
	                            gridLines= js.Dynamic.literal(
	                              drawTicks= true,
	                              drawBorder= false,
	                              display= true,
	                              color= "rgba(255,255,255,0.1)",
	                              zeroLineColor= "transparent"
	                            )
	                          )).toJSArray,
	                          xAxes = Seq(js.Dynamic.literal(
	                            gridLines=js.Dynamic.literal(
	                              zeroLineColor= "transparent",
	                              display= false,
	                            ),
	                            ticks = js.Dynamic.literal(
	                              padding= 10,
	                              fontColor= "rgba(255,255,255,0.4)",
	                              fontStyle= "bold"
	                            )
	                          )).toJSArray
	                        )
	                      )
	                    ))
	                  }))
					  	}, header.get.query.typ, header.get.query.useBody)
            }
            div(
              id := s.asId,
              if (header.isDefined) div(
                cls := "panel-header panel-header-lg",
                canvas(id := mainRef + "header")),
              div(
                cls := "card",
                div(
                  cls := "card-header",
                  h3(s)),
                div(
                  cls := "row",
                  div(
                    cls := "col-md-12",
                    div(
                      cls := "card card-stats",
                      div(
                        cls := "card-body",
                        div(
                          cls := "row",
                          for {
                            ws <- widg.get
                          } yield ws())))))))
          })
        }
        case "login" => {
          if (!mobileSite) {
            dialog("loginfailure", "Login Failure", "Your credentials are invalid!")
            dialog("loginscript", "Login Again", "Website has been updated.", true)
            dialog("dataerror", "Data Error", "No Data! Try again later.")
            dialog("networkerror", "Network Error", "Server problems! Try again later.", true)
            dialog("genericerror", "Error", "An error occured.")
          }
          scriptVal = scriptEl
		  println("Script: " + scriptVal)
          pages += s -> (() => if (mobileSite) {
              val pp = if (store.getItem("rememberme") != null) Some(store.getItem("rememberme").split(",").map(e => (Try(e.split("=")(0)).getOrElse(""), Try(e.split("=")(1)).getOrElse(""))).toMap) else None
              println("Retrieved creds: " + pp)
              onsPage(id := s.asId,
                if (back.isDefined) div(cls := "background", style := "background-image: url('" + back.get + "');"),
                onsToolbar(
                  div(cls := "center", name)),
                  div(style := "text-align: center; margin-top: 200px",
                    onsList(style := "opacity: 0.9;",
                      for (e <- elems.get if (e.typ.equals("text") || e.typ.equals("password"))) yield onsListItem(onsInput(id := e.name.asInput, `type` := e.typ, modifier := "material underbar", placeholder := e.name, Try(value := pp.get(e.name.asInput).replaceAll("undefined", "")).getOrElse(value := ""))),
                      for (e <- elems.get if (e.typ.equals("remember"))) yield onsListItem(onsCheckbox(id := "rememberme", if (store.getItem("rememberme") != null) `checked` := "", e.name)),
                      for (e <- elems.get if e.typ.equals("button")) yield onsListItem(onsButton(id := e.name.asButton, modifier := "large", e.name)),
                      if (elems.get.filter(_.typ.equals("remember")).size > 0) onsListItem(onsButton(id := "forgot", cls := "forgot-password", modifier := "quiet", "Forgot password?"))
                    )
                  )
              )
            } else div(cls := "full-page-background", id := s.asId,
              div(cls := "full-page login-page section-image", attr("filter-color") := "black",
                if (back.isDefined) div(cls := "full-page-background", style := "background-image: url('" + back.get + "')"),
                div(
                  cls := "content",
                  div(
                    cls := "container",
                    div(
                      cls := "col-md-4 ml-auto mr-auto",
                      form(cls := "form", method := "", action := "",
                        div(
                          cls := "card card-login card-plain",
                          div(
                            cls := "card-header"),
                          div(
                            cls := "card-body",
                            for (e <- elems.get if (e.typ.equals("text") || e.typ.equals("password"))) yield e.el),
                            div(
                              cls := "card-footer",
                              for (e <- elems.get if e.typ.equals("button")) yield e.el,
                              div(
                                cls := "pull-left",
                                h6(
                                  a(href := "#pablo", cls := "link footer-link", "Create Account"))),
                              div(
                                cls := "pull-right",
                                h6(
                                  a(href := "#pablo", cls := "link footer-link", "Need Help?"))))))))),
              for (e <- elems.get if e.typ.equals("footer")) yield e.el)))
        }
        case "main" => {
          dialog("dataerror", "Data Error", "No Data! Try again later.")
          dialog("networkerror", "Network Error", "Server problems! Try again later.")
          dialog("genericerror", "Error", "An error occured.")
          if (elems.isDefined) for (e <- elems.get if e.typ.equals("display")) {
            dialog(e.name.asId + "success", e.name + " Success", "Success")
            dialog(e.name.asId + "error", e.name + " Error", "Error")
            g.document.body.appendChild(e.el.render)
            Try(permUpdaters += e.name.asId -> (() => click(e.id.get.asId)))
            Try(permUpdaters += elems.get.filter(e => e.typ.equals("display"))(0).id.get.asInstanceOf[String].asId + "save" -> (() => {
              val obj = js.Dynamic.literal( // tbd
                username = if (!e.query.get.url.contains("mockup")) {
					println("we use a mockup user")
					"mockup"
				} else currentContext.get.response.extractV(e.name).asInstanceOf[String],
                oldpassword = jQuery("#" + elems.get.filter(e => e.typ.equals("display"))(0).id.get.asInstanceOf[String].asId + "oldpasswordin").value,
                newpassword = jQuery("#" + elems.get.filter(e => e.typ.equals("display"))(0).id.get.asInstanceOf[String].asId + "newpasswordin").value
              )
              println("Saving " + stringify(obj))
              if (!e.query.get.url.contains("mockup")) jQuery.ajax(js.Dynamic.literal(
                url = queryFormat(e.query.get, ""),
                data = stringify(obj),
                contentType = "application/json",
                success = { (data: js.Any, textStatus: js.Any, jqXHR: JQueryXHR) => click(e.name.asId + "success")},
                error = { (jqXHR: JQueryXHR, textStatus: js.Any, errorThrow: js.Any) => click(e.name.asId + "error")},
                `type` = e.query.get.typ,
                timeout = 300000
              ).asInstanceOf[JQueryAjaxSettings]) else println("using mockup data")
            }))
          }
          if ("editwindow".notPresent) g.document.body.appendChild(div(cls := "modal fade", id := "editwindow", tabindex := "-1", role := "dialog", attr("aria-labelledby") := "Edit Script", attr("aria-hidden") := "true",
              div(
                cls := "modal-dialog modal-lg",
                div(
                  cls := "modal-content",
                  div(
                    cls := "modal-header justify-content-center",
                    button(`type` := "button", cls := "close", attr("data-dismiss") := "modal", attr("aria-hidden") := "true",
                      i(cls := "now-ui-icons ui-1_simple-remove")),
                    h4(cls := "title title-up", "Edit script")),
                  div(
                    cls := "modal-body",
                    textarea(id := "editcontent", width := "100%", rows := "20", scriptDef)),
                  div(
                    cls := "modal-footer",
                    a(href := "#reload", cls := "btn btn-info", "Reload"),
                    button(`type` := "button", cls := "btn btn-danger", attr("data-dismiss") := "modal", "Close"))))).render)
          if (elems.isDefined) for (e <- elems.get if e.typ.equals("datetimedialog")) {
            g.document.body.appendChild(e.el.render)
            jQuery("#datefrom").datetimepicker("keepOpen", true)
            jQuery("#dateto").datetimepicker("keepOpen", true)
          }
          def isOne = {
	        def one(elem: js.Dynamic, array: List[String]): Boolean = array match {
					case ar :: idd :: name :: rest => {
						val ll = elem.extractV(ar).asInstanceOf[js.Array[js.Dynamic]].toList
						if (array.size > 3 && ll.length < 2) one(ll(0), rest) else ll.length < 2
					}
					case _ => false
	        }
	        val params = menus._1.split(",").toList
		    one(currentContext.get.response, params.drop(1))
          }
           def createMobileMenu = {
            var idx = 0
	          def menuMobile(title: String, titleacc: List[String], elem: js.Dynamic, array: List[String]): JsDom.TypedTag[org.scalajs.dom.raw.Element] = array match {
					    case ar :: idd :: name :: rest => {
					    	val ref = "menuentry" + idx
                idx = idx + 1
                val e = elem.extractV(ar).asInstanceOf[js.Array[js.Dynamic]].toList(0)
                onsListItem(href := "#selection_" + idd + "_" + e.extractV(idd).asInstanceOf[String], tappable := "", e.extractV(name).asInstanceOf[String])
					    }
	          	case _ => onsListItem()
	          }
	          val params = menus._1.split(",").toList
		        menuMobile(params(0), List(), currentContext.get.response, params.drop(1))
          }
          def createMenu = {
            var idx = 0
	          def menu(title: String, titleacc: List[String], elem: js.Dynamic, array: List[String]): JsDom.TypedTag[html.LI] = array match {
					case ar :: idd :: name :: rest => {
					    val ref = "menuentry" + idx
                		idx = idx + 1
					    li(
		              		a(attr("data-toggle") := "collapse", href := "#" + ref,
                    		i(cls := "now-ui-icons business_globe"),
                    		p(title,
                      			b(cls := "caret"))),
		              			div(cls := "collapse", id := ref,
		                		ul(
		                  			cls := "nav",
		                  				for (e <- elem.extractV(ar).asInstanceOf[js.Array[js.Dynamic]].toList) yield
		                    				if (array.size > 3) menu(e.extractV(name).asInstanceOf[String], titleacc ++ List(name) ++ List(e.extractV(name).asInstanceOf[String]), e, rest) else li(
		                      					a(
													href := "#selection_" + idd + "_" + e.extractV(idd).asInstanceOf[String],
													attr("data-info") := (titleacc ++ List(name) ++ List(e.extractV(name).asInstanceOf[String]) ++ List(Try(e.extractV("mediaPath").asInstanceOf[String]).getOrElse(extractMapOrUser("mediaPath")))).mkString("_"),
													span(cls := "sidebar-mini-icon", e.extractV(idd).asInstanceOf[String]),
													span(cls := "sidebar-normal", e.extractV(name).asInstanceOf[String]))))))
					}
	          		case _ => li()
	          	}
	          	val params = menus._1.split(",").toList
				val respp = Try(currentContext.get.response.asInstanceOf[List[String]]).getOrElse(List("One","Two","Three"))
		        menu(params(0), List(), currentContext.get.response, params.drop(1))
          }
          pages += s -> (() => {
            setTimeout(500) {
              jQuery("#maintabbar").children().eq(0).append(div(id := "mainpanel").render)
            }
	          setTimeout(1000) {
	          	Try(replacePanel(elems.get.filter(e => e.typ.equals("load"))(0).name))
	          }
            if (mobileSite) {
              onsSplitter(
                onsSplitterSide(id := "menumobile", side := "left", width := "220px", collapse := "", swipeable := "",
                  onsPage(
                    onsList(
                      createMobileMenu,
                      for (sp <- menu) yield onsListItem(id := sp._1, sp._1, tappable := "", modifier := "chevron")
                    )
                  )
                ),
                onsSplitterContent(id := "content",
                  onsPage(
                    onsToolbar(
                      div(cls := "left",
                        onsToolbarButton(id := "mobilemenuclick",
                          onsIcon(icon := "md-menu")
                        )
                      ),
                      div(cls := "center", s)
                    ),
                    onsTabbar(swipeable := "", id := "maintabbar",
                      for (sp <- menu) yield onsTab(labelOns := sp._1, id := "tab_" + sp._1, icon := "md-menu")
                    )
                  )
                )
              )
            } else div(
              cls := "wrapper",
              div(cls := "sidebar", attr("data-color") := Try(mainColor.get).getOrElse("blue"),
                div(
                  cls := "logo",
                  a(href := "#" + elems.get.filter(e => e.typ.equals("display"))(0).name.asId, cls := "simple-text logo-normal", Try(currentContext.get.response.extractV(elems.get.filter(e => e.typ.equals("display"))(0).name)).getOrElse("Some User").asInstanceOf[String])),
                  for (aa <- menus._1.split(",").drop(1).grouped(3).toList) yield {
                    val cc = Try(currentContext.get.response.extractV(aa(2)).asInstanceOf[String]).getOrElse("")
                    div(
                      cls := "logo",
                      a(	
                          id := "menuinfo" + aa(2),
                          href := "#",
                          cls := "simple-text logo-normal",
                          cc))
                  },
                div(
                  cls := "sidebar-wrapper",
                  if (!isOne) ul(
                    cls := "nav",
                    createMenu()),
                  ul(
                    cls := "nav",
                    for (sp <- menu) yield (if (sp._2.equals("menu")) li(
                      a(attr("data-toggle") := "collapse", href := "#" + sp._1 + "submenu",
                        i(cls := "now-ui-icons business_chart-pie-36"),
                        p(
                          sp._1,
                          b(cls := "caret"))),
                      div(cls := "collapse", id := sp._1 + "submenu",
                        ul(
                          cls := "nav",
                          if (sp._3.isDefined) for (st <- sp._3.get) yield li(
                            a(
                              href := "#" + st,
                              span(cls := "sidebar-mini-icon", st.substring(0, 2)),
                              span(cls := "sidebar-normal", st))))))
                    else li(
                      a(
                        href := "#" + sp._1,
                        i(cls := "now-ui-icons " + (if (sp._2.equals("dashboard")) "design_app" else if (sp._2.equals("table")) "design_bullet-list-67" else if (sp._2.equals("menu")) "design_bullet-list-67" else "design_app")),
                        p(sp._1))))),
                  ul(
                    cls := "nav",
                    for (e <- elems.get.filter(e => e.typ.equals("link"))) yield {
                        val idd = UUID.uuid.replaceAll("-","")
                        setTimeout(1000) {
                          jQuery(document).on("click", "#" + idd, (ev: JQueryEventObject) => window.open(queryFormat(e.query.get, ""),"_blank"))
                        }
                        li(
                        a(
                          id := idd,
                          i(cls := "now-ui-icons design_app"), p(e.name)))
                  })
                )),
              div(
                cls := "main-panel",
                nav(
                  cls := "navbar navbar-expand-lg navbar-transparent navbar-absolute",
                  div(
                    cls := "container-fluid",
                    div(
                      cls := "navbar-wrapper",
                      div(
                        cls := "navbar-toggle",
                        button(`type` := "button", cls := "navbar-toggler",
                          span(cls := "navbar-toggler-bar bar1"),
                          span(cls := "navbar-toggler-bar bar2"),
                          span(cls := "navbar-toggler-bar bar3"))),
                      a(cls := "navbar-brand", href := "#pablo", s)),
                    button(cls := "navbar-toggler", `type` := "button", attr("data-toggle") := "collapse", attr("data-target") := "#navigation", attr("aria-controls") := "navigation-index", attr("aria-expanded") := "false", attr("aria-label") := "Toggle navigation",
                      span(cls := "navbar-toggler-bar navbar-kebab"),
                      span(cls := "navbar-toggler-bar navbar-kebab"),
                      span(cls := "navbar-toggler-bar navbar-kebab")),
                    div(cls := "collapse navbar-collapse justify-content-end", id := "navigation",
                      ul(
                        cls := "navbar-nav",
                        li(
                          cls := "nav-item",
                          id := "navigationprogress"),
                        li(
                          cls := "nav-item",
                          a(cls := "nav-link", href := "#dates",
                            i(cls := "now-ui-icons media-1_album"))),
                        li(
                          cls := "nav-item",
                          a(cls := "nav-link", href := "#print",
                            i(cls := "now-ui-icons files_paper"))),
                        if (Try(currentContext.get.response.extractV("username").asInstanceOf[String].startsWith("admin")).getOrElse(false)) i(
                          cls := "nav-item",
                          a(cls := "nav-link", href := "#edit",
                            i(cls := "now-ui-icons objects_key-25"))),
                        li(
                          cls := "nav-item",
                          a(cls := "nav-link", href := "#reload",
                            i(cls := "now-ui-icons media-1_button-power"))))))),
                div(cls := "panel-header panel-header-sm", style := js.Dictionary("width" -> "100%", "height" -> "3em", "padding-top" -> "20px")),
                div(id := "mainpanel", cls := "content", style := "height: 100vh; overflow-y: auto;"),
                  for (e <- elems.get if e.typ.equals("footer")) yield e.el))
            })
        }
        case "menu" => {
          menu = menu ++ List((s, t, Some(submenu)))
          submenu = List[String]()
        }
        case "page" => {
          menu = menu ++ List((s, t, None))
          pages += s -> (() => onsPage(id := s.asId,
            onsList(modifier := "inset",
              onsListHeader(lines.get(0)),
              for (s <- lines.get.drop(1)) yield onsListItem(modifier := "longdivider",
                div(cls := "center", s)
              ))
            )
          )
        }
        case "table" => {
          menu = menu ++ List((s, t, None))
          pages += s -> (() => if (static.isDefined && static.get.l.size > 0) {
            def produce(accum: String, header: String, static: StaticStruct, prev: Option[org.scalajs.dom.Node] = None): JsDom.TypedTag[org.scalajs.dom.raw.Element] = {
              lazy val pp: JsDom.TypedTag[org.scalajs.dom.raw.Element] = onsPage(id := s.asId,
                p(cls :="search-bar", style := "text-align: center; margin-top: 10px;",
                  div(id := s.asId + "list",
                    onsList(
                      onsListHeader(header),
                      for (el <- static.l) yield {
                        val idd = UUID.uuid.replaceAll("-","")
                        if (el.sp.isDefined) jQuery(document).on("click", "#" + idd, (ev: JQueryEventObject) => {
                          currPanel = ""
                          jQuery("#mainpanel").empty()
                          jQuery("#mainpanel").append(produce(accum + "/" + el.value, el.value, el.sp.get, Some(pp.render)).render)
                        })
                        if (el.back && prev.isDefined) jQuery(document).on("click", "#" + idd, (ev: JQueryEventObject) => {
                          currPanel = ""
                          jQuery("#mainpanel").empty()
                          jQuery("#mainpanel").append(prev.get)
                        })
						val dataStore = accum + "/" + el.value + "/type=" + el.typ
                        val hier = (accum + "/" + el.value).split("/").filter(!_.isEmpty)
                        val obj = getObject("data")
                        println("Retrieving " + stringify(obj))
                        val vv = obj.extract(hier)
						println("dataStore: " + dataStore + " -> " + (if (vv != null) vv.toString else "None"))
                        el.typ match {
                          case "checkbox" => onsListItem(modifier := "longdivider", onsCheckbox(id := idd, modifier := "large", el.value, attr("data-store") := dataStore, if (vv != null && vv.asInstanceOf[Boolean]) `checked` := ""))
                          case "text" => {
                              if (el.ws.isDefined && vv != null) setTimeout(1000) {
                                    println("Websocket defined for " + el.value + " at " + el.ws.get)
                                    wsconn = Some(new WebSocket(el.ws.get + "?" + el.value + "=" + vv.asInstanceOf[String]))
                                    wsconn.get.onopen = { (event: Event) => {
                                        println("Connection successful: " + event)
                                        jQuery("#" + idd).animate(js.Dynamic.literal(background = "green"),1000)
                                        jQuery("#" + idd).css(js.Dynamic.literal(background = "green"))
                                    }}
                                    wsconn.get.onerror = { (event: Event) => {
                                        println("Connection error: " + event.asInstanceOf[ErrorEvent])
                                        jQuery("#" + idd).animate(js.Dynamic.literal(background = "red"),1000)
                                        jQuery("#" + idd).css(js.Dynamic.literal(background = "red"))
                                    }}
                                    wsconn.get.onmessage = { (event: MessageEvent) => println("Message: " + event.data.toString) }
                                    wsconn.get.onclose = { (event: Event) => {
                                        println("Connection closed: " + event.asInstanceOf[CloseEvent])
                                        jQuery("#" + idd).animate(js.Dynamic.literal(background = "red"),1000)
                                        jQuery("#" + idd).css(js.Dynamic.literal(background = "red"))
                                    }}
                              }
                              onsListItem(onsInput(id := idd, `type` := el.typ, placeholder := el.value, attr("data-store") := dataStore, if (vv != null) attr("value") := vv.asInstanceOf[String]))
                          }
                          case "signature" => {
                            setTimeout(1000) {
                                val canvas = jQuery("#" + idd).get(0).asInstanceOf[html.Canvas]
                                if (vv != null) {
                                    val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
                                    var image = dom.document.createElement("img").asInstanceOf[HTMLImageElement]
                                    image.src = vv.toString
                                    image.onload = (e: dom.Event) => {
                                        ctx.drawImage(image, 0, 0)
                                    }
                                }
                                sigs += idd -> new SignaturePad(canvas)
                            }
                            onsListItem(modifier := "longdivider", canvas(id := idd, style := "background-color: lightGrey;", attr("data-store") := dataStore, width := /*(window.innerWidth / 2) +*/ "300px", height := /*(window.innerHeight / 2) +*/ "150px"))
                          }
                          case _ => onsListItem(modifier := "longdivider", onsButton(id := idd, modifier := "large", el.value, attr("data-store") := dataStore))
                        }
                      }
                    )
                  )
                )
              )
              pp
            }
            produce("", s, static.get)
          } else {
            searchablePanel = Some(s)
            tableDiv(title, message, footer, queryFormat(disp.get, if (!disp.get.typ.toUpperCase.equals("GET")) "" else if (searchValue.isEmpty) {
                if (toSetDate.isEmpty) "order=desc" + Try("&days=" + days.get.toInt).getOrElse("") else "order=desc&startDate=" + fromSetDate + "&endDate=" + toSetDate
              } else "search=" + searchValue), s, disp.get.params.get, disp.get.typ, disp.get.useBody, Try(itemsNum.get.toInt).getOrElse(10), pl, trans, static)
          })
        }
        case _ =>
      }
      if (sh.isDefined) g.document.body.appendChild(pages(s)().render)
      logic
   }
  }

  case class ScriptCls(url: String, key: Option[String])
  def scriptTok = stringToken ~ (("key" ~> stringToken)?) ^^ {
    case url ~ key => ScriptCls(url, key)
  }

  case class Header(title: String, timeField: Option[String], query: Query)
  def header = "header" ~> stringToken ~ (("time" ~> stringToken)?) ~ ("{" ~> query <~ "}") ^^ {
  	case title ~ t ~ q => Header(title, t, q)
  }
 
  implicit class ps(s: String) {
    def asId = s.toLowerCase.replaceAll("""[\t\n\v\f\r\.\s'#\[\]=\@]+""", "_").replaceAll("  ", "").replaceAll(" ","")
    def asButton = s.asId + "btn"
    def asInput = s.asId + "in"
    def notPresent = Try(jQuery("#" + s).length == 0).getOrElse(true)
  }

  val indexed = "([A-Za-z]+)\\(([0-9]+)\\)".r

    implicit class pjd(o: js.Dictionary[js.Dynamic]) {
       def extract(a: Array[String]) = a.foldLeft(o)((oo, ss) => Try({
           val mm = oo.toMap
           mm(ss).asInstanceOf[js.Dictionary[js.Dynamic]]
        }).getOrElse(null.asInstanceOf[js.Dictionary[js.Dynamic]]))
    }

    implicit class pjs(o: js.Dynamic) {
        def extractV(s: String) = {
            s.split("\\.").foldLeft(o)((oo, ss) => {
                val m = oo.asInstanceOf[js.Dictionary[js.Dynamic]].toMap
                Try({
                    val indexed(field, idx) = ss
                    val aa = m(field).asInstanceOf[js.Array[js.Dynamic]].toArray
                    aa(idx.toInt)
                }).getOrElse(m(ss))
            })
        }
    }

  def printme = js.Function {
    window.print()
    ""
  }

  case class ErrorMsg(value: String) extends AnyVal {
    override def toString: String = value
  }

  def get[T: ClassTag](elementId: String): Either[ErrorMsg, T] = {
    val queryResult = document.querySelector(s"#$elementId")
    queryResult match {
      case elem: T => Right(elem)
      case other => Left(ErrorMsg(s"Element with ID $elementId is $other"))
    }
  }

  def getContext2D(canvas: html.Canvas): Either[ErrorMsg, CanvasRenderingContext2D] =
    if (canvas != null)
      canvas.getContext("2d") match {
        case context: CanvasRenderingContext2D => Right(context)
        case other => Left(ErrorMsg(s"getContext(2d) returned $other"))
      }
    else
      Left(ErrorMsg("Can't get rendering context of null canvas"))
 
  def reports = "add" ~> stringToken ~ (("title" ~> stringToken)?) ~ (("message" ~> stringToken)?) ~ (("footer" ~> stringToken)?) ~ "report" ~ (("items" ~> wholeNumber)?) ~ (wholeNumber?) ~ (("{" ~> query <~ "}")?) ^^ {
    case ws ~ title ~ message ~ footer ~ tp ~ itemsNum ~ days ~ q => {
      tp match {
        case "report" => {
          submenu = submenu ++ List(ws)
          pages += ws -> (() => {
            searchablePanel = Some(ws.asId)
            tableDiv(title, message, footer, queryFormat(q.get, if (!q.get.typ.toUpperCase.equals("GET")) "" else if (searchValue.isEmpty) {
            	if (toSetDate.isEmpty) "order=desc" + Try("&days=" + days.get.toInt).getOrElse("") else "order=desc&startDate=" + fromSetDate + "&endDate=" + toSetDate
            } else "search=" + searchValue), ws, q.get.params.get, q.get.typ, q.get.useBody, Try(itemsNum.get.toInt).getOrElse(10))
          })
        }
      }
    }
  }

  def trunc(x: Double, n: Int) = {
	  def p10(n: Int, pow: Long = 10): Long = if (n==0) pow else p10(n-1,pow*10)
  	if (n < 0) {
    	val m = p10(-n).toDouble
    	math.round(x/m) * m
  	} else {
   	 	val m = p10(n).toDouble
    	math.round(x*m) / m
  	}
	}

  var charts = Map[String,js.Dynamic]()
  val updaters = scala.collection.mutable.Map[String, (Int, Boolean) => Unit]()
  val permUpdaters = scala.collection.mutable.Map[String, () => Unit]()
  var widgetRef = ""

  def resetWidget(mainRef: String, title: String, value: Int, calendar: Boolean) {
    jQuery("#" + mainRef + "range").remove()
    jQuery("#" + mainRef + "footer").append(div(
      id := mainRef + "range",
      cls := "stats",
      i(cls := "now-ui-icons arrows-1_refresh-69"),
      title).render)
    updaters(mainRef)(value, calendar)
  }

  def widgets = "add" ~> stringToken ~ ("customers" | "messages" | "total" | "doughnut" | "revenue" | "support") ~ ("top"?) ~ (stringToken?) ~ (("time" ~> stringToken)?) ~ (("days" ~> "{" ~> rep(menuRange) <~ "}")?) ~ (("use" ~> "{" ~> query <~ "}")?) ^^ {
    case ws ~ tp ~ top ~ title ~ timefield ~ days ~ q => {
      println("recognizing widget " + ws + " type " + tp + " title " + title + " days " + days)
      val mainRef = ws.asId + UUID.uuid.replaceAll("-","")
      def choices() = for (i <- days.get) yield button(id := mainRef + "_" + i.value, cls := "dropdown-item", i.title)
      def fill(extra: List[Extra], queryStr: String, typ: String) = if (!queryStr.contains("mockup")) Try({
        setTimeout(1000) {
          jQuery.ajax(js.Dynamic.literal(
					    url = queryFormat(Query(queryStr, false, typ, Some(extra), None, None), "limit=" + 10000),
					    success = { (data: js.Any, textStatus: js.Any, jqXHR: JQueryXHR) =>
                val l = data.asInstanceOf[js.Array[js.Dynamic]].toList
                jQuery("#" + mainRef).text("" + l.length)
					    },
					    error = { (jqXHR: JQueryXHR, textStatus: js.Any, errorThrow: js.Any) =>
					      println(s"jqXHR=$jqXHR,text=$textStatus,err=$errorThrow")
  				      click("networkerror")
					    },
					    `type` = typ
					).asInstanceOf[JQueryAjaxSettings])
        }
      }) else println("using mockup data")
      tp match {
        case "customers" => () => {
          fill(q.get.extras.get, q.get.url, q.get.typ)
          div(
            cls := "col-md-3",
            div(
              cls := "statistics",
              div(
                cls := "info",
                div(
                  cls := "icon icon-info",
                  i(cls := "now-ui-icons users_single-02")),
                h3(id := mainRef, cls := "info-title", ""),
                h6(cls := "stats-title", ws))))
        }
        case "messages" => () => {
          div(
            cls := "col-md-3",
            div(
              cls := "statistics",
              div(
                cls := "info",
                div(
                  cls := "icon icon-primary",
                  i(cls := "now-ui-icons ui-2_chat-round")),
                h3(cls := "info-title", "859"),
                h6(cls := "stats-title", ws))))
        }
        case "doughnut" => () => {
        	//setTimeout(1100) {
        	//	logic
        	//}
          updaters += mainRef -> ((dd: Int, calendar: Boolean) => setTimeout(1000) {
            Try({
            	charts(mainRef).destroy()
            	charts -= mainRef
            })
            val now = Moment().unix() * 1000L
            val nowStr = Moment().local().format()
            val factor = if (dd == 30) (Moment().date(), "month") else if (dd == 7) (Moment().day(), "week") else (1, "day")
            val from = Try(now - 86400000L * (if (calendar) factor._1 else dd)).getOrElse(now - 86400000L * 30)
            val fromStr = Moment(from).local().format()
            println("Range for doughnut is " + fromStr + " to " + nowStr + " with " + dd + " days.")
            get[html.Canvas](mainRef + "canvas").fold(
              errorMsg => println("Could not find canvas. Error is {}", errorMsg),
              canvas => getContext2D(canvas).fold(
                errorMsg => println("Couldn't get rendering context of canvas: {}. Error: {}", canvas, errorMsg),
                context => {
                    queryServer(queryFormat(q.get, "days=" + (dd + (if (calendar) factor._1 else dd))), (data: js.Any) => {
	                    val l = data.asInstanceOf[js.Array[js.Dynamic]].toList
	                    val pr = l.foldLeft((0.0,0.0))((s,v) => {
	                      val dt = Try(Moment(v.extractV(timefield.get).asInstanceOf[Int])).getOrElse(Moment(v.recordCreated.asInstanceOf[String])).toDate()
	                      val vv = Try(evaluate(v, v.extractV(q.get.params.get.head.exp.td(0).name).asInstanceOf[Double], q.get.params.get.head.exp.op)).getOrElse(Try(v.extractV(q.get.params.get.head.exp.td(0).name).asInstanceOf[Double]).getOrElse(1.0))
	                      if (dt.getTime() < from) (s._1 + vv.asInstanceOf[Double], s._2) else (s._1, s._2 + vv.asInstanceOf[Double])
	                    })
	                    val points = ChartData(Seq("Current " + dd, "Previous " + dd),  js.Array(ChartDataset(Seq(trunc(pr._2,1), trunc(pr._1,1)), Seq("" + trunc(pr._2,1), "" + trunc(pr._1,1)))))
	                    val fontSize = canvas.height.asInstanceOf[Double] / 10
	                    val config = ChartConfiguration("doughnut", points, js.Dynamic.literal(
	                        responsive = true,
	                        legend = js.Dynamic.literal(
														display = false,
														position = "top",
													),
													title = js.Dynamic.literal(
														display = top.isDefined,
														fontSize = 20,
														text = title.getOrElse("").asInstanceOf[js.Any],
													),
													animation = js.Dynamic.literal(
														animateScale = true,
														animateRotate = true
													),
	                        plugins = js.Dynamic.literal(
														doughnutlabel = js.Dynamic.literal(
															labels = Seq(
																js.Dynamic.literal(
																	text = Try(q.get.params.get.head.alias).getOrElse("Qty").asInstanceOf[js.Any],
																	font = js.Dynamic.literal(
																		size = "" + fontSize,
																		family = "Arial",
																		style = "italic",
																		weight = "bold",
																	),
																	color = "#111111"
																),
																js.Dynamic.literal(
																	text = if (pr._1 != 0.0) "" + (pr._2 * 100.0 / pr._1).asInstanceOf[Int] + "%" else "N/A",
																	font = js.Dynamic.literal(
																		size = "" + fontSize,
																		family = "Arial",
																		style = "italic",
																		weight = "bold",
																	),
																	color = "#bc2c1a"
																)
															).toJSArray
														)
													)
	                      )
	                    )
	                    val chart = (new JSChart(context, config)).asInstanceOf[js.Dynamic]
	                    charts += mainRef -> chart
								    }, q.get.typ, q.get.useBody)
               }))
          })
          updaters(mainRef)(days.get(0).value, days.get(0).calendar)
          setTimeout(500) {
            jQuery("#" + mainRef + "_custom").on("click", (e: JQueryEventObject) => {
              widgetRef = mainRef
              click("dateswindow")
            })
          	Try(days.get.foreach(d =>  {
              println("Added " + d + " for " + mainRef)
              jQuery("#" + mainRef + "_" + d.value).on("click", (e: JQueryEventObject) => resetWidget(mainRef, d.title, d.value, d.calendar))
	          }))
          }
          div(
            id := mainRef + "pie",
            cls := "col-lg-4 col-md-6",
            div(
              cls := "card card-chart",
              div(
                cls := "card-header",
                h5(cls := "card-category", a(
                  href := "#" + ws,
                  p(ws + " " + (if (!top.isDefined) title.getOrElse("").asInstanceOf[js.Any] else "")))),
                div(
                  id := mainRef + "dropdown",
                  cls := "dropdown",
                  button(`type` := "button", cls := "btn btn-round btn-default dropdown-toggle btn-simple btn-icon no-caret", attr("data-toggle") := "dropdown",
                    i(cls := "now-ui-icons loader_gear")),
                  div(
                    cls := "dropdown-menu dropdown-menu-right",
                    button(id := mainRef + "_custom", cls := "dropdown-item", "Custom..."),
                    if (days.isDefined) choices))),
              div(
                cls := "card-body",
                div(
                  cls := "table-responsive",
                  div(id := mainRef + "container", cls := "chart-container", style := js.Dictionary("position" -> "relative", "height" -> "40vh", "width" -> "40vw"),
                    canvas(id := mainRef + "canvas")))),
              div(
			          id := mainRef + "footer",
                cls := "card-footer",
                div(
  			          id := mainRef + "range",
                  cls := "stats",
                  i(cls := "now-ui-icons arrows-1_refresh-69"),
                  days.get(0).title))))
         }
        case "revenue" => () => {
          fill(q.get.extras.get, q.get.url, q.get.typ)
          div(
            cls := "col-md-3",
            div(
              cls := "statistics",
              div(
                cls := "info",
                div(
                  cls := "icon icon-success",
                  i(cls := "now-ui-icons business_money-coins")),
                h3(
                  id := mainRef,
                  cls := "info-title",
                  small("$"),
                  ""),
                h6(cls := "stats-title", ws))))
        }
        case "total" => () => {
          fill(q.get.extras.get, q.get.url, q.get.typ)
          div(
            cls := "col-lg-4 col-md-6",
            div(
              cls := "card card-chart",
              div(
                cls := "card-header",
                h5(cls := "card-category", a(
                  href := "#" + ws,
                  p(ws))),
                h2(cls := "card-title", tp),
                div(
                  cls := "dropdown",
                  button(`type` := "button", cls := "btn btn-round btn-default dropdown-toggle btn-simple btn-icon no-caret", attr("data-toggle") := "dropdown",
                    i(cls := "now-ui-icons loader_gear")),
                  div(
                    cls := "dropdown-menu dropdown-menu-right",
                    a(id := mainRef + "_custom", cls := "dropdown-item", href := "#dates", "Custom..."),
                    if (days.isDefined) choices))),
              div(
                cls := "card-body",
                div(
                  cls := "table-responsive",
                  table(
                    cls := "table",
                    tbody(
                      tr(
                        td("Qty"),
                        td(id := mainRef, cls := "text-right", "")))))),
              div(
                cls := "card-footer",
                div(
                  cls := "stats",
                  i(cls := "now-ui-icons arrows-1_refresh-69"),
                  "Just Updated"))))
        }
        case "support" => () => {
          fill(q.get.extras.get, q.get.url, q.get.typ)
          div(
            cls := "col-md-3",
            div(
              cls := "statistics",
              div(
                cls := "info",
                div(
                  cls := "icon icon-danger",
                  i(cls := "now-ui-icons objects_support-17")),
                h3(id := mainRef, cls := "info-title", ""),
                h6(cls := "stats-title", ws))))
        }
        case _ => () => div()
      }
    }
  }

  case class MenuRange(title: String, value: Int, calendar: Boolean)
  def menuRange = ("calendar"?) ~ stringToken ~ wholeNumber ^^ {
    case c ~ s ~ n => MenuRange(s, n.toInt, c.isDefined)
  }
 
  case class Element(name: String, typ: String, query: Option[Query], tr: Option[Transition], id: Option[String], el: JsDom.TypedTag[org.scalajs.dom.Element] = div())
  def elements = "add" ~> stringToken ~ ("datetimedialog" | "text" | "password" | "button" | "footer" | "display" | "load" | "menu" | "remember" | "link") ~ (stringToken?) ~ (("with" ~> "{" ~> query <~ "}")?) ~ (("with" ~> "{" ~> transition <~ "}")?) ~ (("buttons" ~> "{" ~> rep(rangebtn) <~ "}")?) ^^ {
    case el ~ et ~ tt ~ ad ~ t ~ d => {
      println("recognizing element " + el + " type " + et)
      et match {
        case "button" => {
          if (t.isDefined) transitions = transitions ++ List(Context(element = el.asButton, tr = t.get))
          Element(el, et, ad, t, tt, a(href := "#" + el.asButton, cls := "btn btn-primary btn-round btn-lg btn-block mb-3", el))
        }
        case "datetimedialog" => {
	        val now = Moment().unix() * 1000L
	        val nowStr = Moment(now).format("MM/DD/YYYY HH:MM A")
	        val fromT = now - 86400000L * 30
	        val fromStr = Moment(fromT).local().format("MM/DD/YYYY HH:MM A")
					Element(el, et, ad, t, tt, div(cls := "modal fade", id := "dateswindow", tabindex := "-1", role := "dialog", attr("aria-labelledby") := "Dates", attr("aria-hidden") := "true",
            div(
              cls := "modal-dialog",
              div(
                cls := "modal-content",
                div(
                  cls := "modal-header justify-content-center",
                  button(`type` := "button", cls := "close", attr("data-dismiss") := "modal", attr("aria-hidden") := "true",
                    i(cls := "now-ui-icons ui-1_simple-remove")),
                  h4(cls := "title title-up", "Define Range")),
                div(
                  cls := "modal-body",
                  div(cls := "card",
										div(cls := "card-header"),
											div(cls := "card-body",
											div(cls := "form-row",
                        div(cls := "form-group col-md-5",
                        input(cls := "form-control datetimepicker-input", id := "datefrom", attr("data-toggle") := "datetimepicker", attr("data-target") := "#datefrom")),
                      div(cls := "form-group col-md-1",
                        i(cls := "now-ui-icons arrows-1_minimal-right")),
                      div(cls := "form-group col-md-5",
                        input(cls := "form-control datetimepicker-input", id := "dateto", attr("data-toggle") := "datetimepicker", attr("data-target") := "#dateto"))),
											div(cls := "form-row", for (oo <- d.get) yield oo)))),
								div(
                  cls := "modal-footer",
                  a(href := "#setdate", cls := "btn btn-info", "Set"),
                  button(`type` := "button", cls := "btn btn-danger", attr("data-dismiss") := "modal", "Close"))))))
         }
        case "remember" => Element(el, et, ad, t, tt, onsCheckbox(id := "rememberme", value := el, if (true) `checked` := ""))
        case "footer" => Element(el, et, ad, t, tt, footer(
          cls := "footer",
          div(
            cls := "container-fluid",
            nav(
              ul(
                li(
                  a(href := "#", el)))))))
        case "menu" => {
          menus = (el, t.get.name)
        	Element(el, et, ad, t, tt)
        }
        case "link" => {
        	Element(el, et, ad, t, tt)
        }
        case "display" => Element(el, et, ad, t, tt, div(cls := "modal fade", id := Try(tt.get.asId).getOrElse("idmissing"), tabindex := "-1", role := "dialog", attr("aria-labelledby") := Try(tt.get).getOrElse("idmissing"), attr("aria-hidden") := "true",
          div(
            cls := "modal-dialog",
            div(
              cls := "modal-content",
              div(
                cls := "modal-header justify-content-center",
                button(`type` := "button", cls := "close", attr("data-dismiss") := "modal", attr("aria-hidden") := "true",
                  i(cls := "now-ui-icons ui-1_simple-remove")),
                if (tt.isDefined) h4(cls := "title title-up", tt.get)),
              div(
                cls := "modal-body",
                if (ad.isDefined && ad.get.inparams.isDefined) for (e <- ad.get.inparams.get if !e.hidden) yield div(cls := "form-group has-label", "", input(id := tt.get.asId + e.inputField, cls := "form-control", placeholder := e.field, `type` := e.typ))),
              div(
                cls := "modal-footer",
                a(href := "#" + Try(tt.get.asId).getOrElse("idmissing") + "save", cls := "btn btn-info", attr("data-dismiss") := "modal", "Save"),
                button(`type` := "button", cls := "btn btn-danger", attr("data-dismiss") := "modal", "Close"))))))
        case _ => Element(el, et, ad, t, tt, div(
          cls := "input-group no-border form-control-lg",
          div(
            cls := "input-group-prepend",
            span(
              cls := "input-group-text",
                i(cls := "now-ui-icons users_circle-08"))),
          input(id := el.asInput, cls := "form-control", placeholder := el, `type` := et)))
      }
    }
  }

  def rangebtn = "add" ~> stringToken ~ wholeNumber ^^ {
  	case value ~ days => div(cls := "form-group col-md-3", a(href := "#setrange" + days, cls := "btn btn-primary",value))
  }

  case class Signature(typ: String, title: String, id: String)
  def signature = ("signature" | "camera") ~ stringToken ~ ("as" ~> stringToken) ^^ {
    case d ~ t ~ id => Signature(d, t, id)
  }

  case class Panel(title: String, expr: Option[Expr], query: Option[Query], signature: Option[Signature])
  def panel = "panel" ~> stringToken ~ (("{" ~> expr <~ "}")?) ~ (("{" ~> query <~ "}")?) ~ (signature?) ^^ {
    case t ~ e ~ q ~ s => Panel(t, e, q, s)
  }

  case class Go(name: String, url: Option[Query])
  def go = "go" ~> stringToken ~ (("with" ~> "{" ~> query <~ "}")?) ^^ {
    case n ~ u => Go(n, u)
  }

  case class Query(url: String, useBody: Boolean, typ: String, extras: Option[List[Extra]], params: Option[List[Param]], inparams: Option[List[UrlParam]])
  def query = "query" ~> (("post" | "get" | "put")?) ~ (("body")?) ~ stringToken ~ (("use" ~> "{" ~> rep(extras) <~ "}")?) ~ (("with" ~> "{" ~> rep(params) <~ "}")?) ~ (("inputs" ~> "{" ~> rep(urlParam) <~ "}")?) ^^ {
    case t ~ b ~ url ~ extra ~ pa ~ ia => Query(url, b.isDefined, if (t.isDefined) t.get else "get", extra, pa, ia)
  }

  case class Dropdown(url: String, blank: Boolean, extras: Option[List[Extra]])
  def dropdown = "dropdown" ~> ("withblank"?) ~ stringToken ~ (("use" ~> "{" ~> rep(extras) <~ "}")?) ^^ {
    case b ~ url ~ extra => Dropdown(url, b.isDefined, extra)
  }

  case class Extra(typ: String, value: Option[String], params: Option[List[UrlParam]])
  def extras = ("rest" | "param" | "object") ~ (stringToken?) ~ (("with" ~> "{" ~> rep(urlParam) <~ "}")?) ^^ {
    case typ ~ str ~ params => Extra(typ, str, params)
  }
 
  case class Formats(format: Option[String], dropdownId: Option[String], dropdown: Option[Dropdown])
  def formats = (("format" ~> stringToken)?) ~ (("is" ~> stringToken)?) ~ (dropdown?) ^^ {
    case cf ~ id ~ d => {
      val f = Formats(cf, id, d)
      Try(dropdownsLoad += d.get.url -> f)
      f
    }
  }

  case class Param(exp: Expr, alias: String, typ: String, formats: Formats, delete: Boolean = false)
  def params = ("add" | "column" | "edit" | "media" | "value") ~ ("with delete"?) ~ expr ~ ("as" ~> stringToken) ~ formats ^^ {
    case cc ~ del ~ cn ~ cv ~ cf => Param(cn, cv, cc, cf, del.isDefined)
  }

  case class Expr(td: List[TypeDef], op: Option[List[Operator]])
  def expr: Parser[Expr] = rep(typeDef) ~ (rep(oper)?) ^^  {
  	case vv ~ op => Expr(vv, op)
  }

  case class TypeDef(name: String, alias: String, typ: String, formats: Formats, multi: Option[Expr], required: Boolean)
  def typeDef = stringToken ~ (("is" ~> stringToken)?) ~ (("type" ~> ("Boolean" | "Number"))?) ~ formats ~ (("multi" ~> "{" ~> expr <~ "}")?) ~ ("required"?) ^^ {
  	case ss ~ cc ~ tt ~ f ~ m ~ r => TypeDef(ss, Try(cc.get).getOrElse(ss), Try(tt.get).getOrElse("String"), f, m, r.isDefined)
  }

  case class Operator(op: String, value: Option[Double], variable: Option[String])
  def oper = ("/" | "*" | "+" | "-") ~ ((floatingPointNumber)?) ~ ((stringToken)?) ^^ {
  	case oo ~ nn ~ ss => Operator(oo, Try(Some(nn.get.toDouble)).getOrElse(None), ss)
  }

  @tailrec
  def evaluate(e: js.Dynamic, v: Double, op: Option[List[Operator]]): Double = op.get match {
    case Nil => v
    case x :: xs => x.op match {
      case "/" => evaluate(e, Try(v / x.value.get).getOrElse(Try(v / e.extractV(x.variable.get).asInstanceOf[Double]).getOrElse(1.0)), Some(xs))
      case "*" => evaluate(e, Try(v * x.value.get).getOrElse(Try(v * e.extractV(x.variable.get).asInstanceOf[Double]).getOrElse(1.0)), Some(xs))
      case "+" => evaluate(e, Try(v + x.value.get).getOrElse(Try(v + e.extractV(x.variable.get).asInstanceOf[Double]).getOrElse(1.0)), Some(xs))
      case "-" => evaluate(e, Try(v - x.value.get).getOrElse(Try(v - e.extractV(x.variable.get).asInstanceOf[Double]).getOrElse(1.0)), Some(xs))
    }
  }

  case class Transition(name: String, url: Option[Query])
  def transition = "transition" ~> stringToken ~ (("with" ~> "{" ~> query <~ "}")?) ^^ {
    case n ~ u => Transition(n, u)
  }

  case class UrlParam(field: String, inputField: String, sha256: Boolean, hidden: Boolean, typ: String, fixed: Boolean = false)
  def urlParam = stringToken ~ ("field" | "using" | "is") ~ stringToken ~ ("sha256"?) ~ ("hidden"?) ~ ("password"?) ^^ {
    case f ~ t ~ v ~ sha ~ hid ~ typ => UrlParam(f, if (t.equals("using")) v.asInput else v, sha.isDefined, hid.isDefined, if (typ.isDefined) typ.get else "edit", t.equals("is"))
  }

  def stringToken = stringLiteral ^^ (_.toString.drop(1).dropRight(1))

  var currentTable: String = _
  var currentCols: List[Param] = _
  def tableDiv(titl: Option[String], message: Option[String], footer: Option[String], queryStr: String, s: String, cols: List[Param], typ: String = "GET", useBody: Boolean, numItems: Int = 10, panels: Option[List[Panel]] = None, goto: Option[List[Go]] = None, static: Option[StaticStruct] = None) = {
    var lastFormat = "MM/DD/YYYY"
    mobileContext.empty
    currentTable = s
    currentCols = cols
    def getFormat(e: js.Dynamic, cn: Param) = {
      val fs = Try(e.extractV(cn.exp.td(0).name).asInstanceOf[String]).getOrElse("")
      if (fs.isEmpty || cn.formats.format.isEmpty) fs else Try(Try(Try(cn.formats.format.get.format(evaluate(e, fs.toDouble, cn.exp.op))).getOrElse(evaluate(e, fs.toDouble, cn.exp.op).toString)).getOrElse(cn.formats.format.get.format(fs.toDouble))).getOrElse(Try(Moment(fs.substring(0, 19)).format(cn.formats.format.get)).getOrElse(fs))
    }
    queryServer(queryStr, (data: js.Any) => {
      val l = data.asInstanceOf[js.Array[js.Dynamic]].toList
      if (mobileSite) {
        def getCols(e: js.Dynamic, cols: List[Param]) = {
          val rowId = UUID.uuid.replaceAll("-","")
          def getClass(i: Int) = i match {
            case 0 => "left"
            case 1 => "center"
            case 2 => "right"
            case 3 => "expandable-content"
            case _ => ""
          }
          def getListItem(e: js.Dynamic, rowId: String, cols: List[Param]) = onsListItem(id := rowId, modifier := "chevron", attr("data-object") := stringify(e), tappable := "", for (cn <- cols.filter(!_.typ.equals("add")).zipWithIndex) yield Try({
            val idd = rowId + cn._1.exp.td(0).name.asId
            cn._1.typ match {
              case "column" => {
                if (cn._1.formats.format.isDefined && cn._1.formats.format.get.equals("Boolean")) {
                  val fs = e.extractV(cn._1.exp.td(0).name).asInstanceOf[Boolean]
                  setTimeout(1000) {
                    println("Unbound " + idd + "switch")
                    jQuery("#" + idd + "switch").unbind("click")
                    jQuery(document).on("click", "#" + idd + "switch", (ev: JQueryEventObject) => {
                      val m = e.asInstanceOf[js.Dictionary[js.Dynamic]].toMap
                      val bb = !jQuery("#" + idd + "switch").attr("data-switch").asInstanceOf[String].equals("true")
                      println("Here will store immediately " + stringify(e) + " with " + bb.asInstanceOf[Boolean])
                      val ar = cn._1.exp.td(0).name.split("\\.")
                      val mo = if (ar.size == 1) m ++ Map(ar(0) -> bb) else {
                        val m1 = m(ar(0)).asInstanceOf[js.Dictionary[js.Dynamic]].toMap
                        val m2 = m1 ++ Map(ar(1) -> bb)
                        m ++ Map(ar(0) -> m2.toJSDictionary.asInstanceOf[js.Dynamic])
                      }
                      val o = mo.toJSDictionary.asInstanceOf[js.Dynamic]
                      println(stringify(o))
                      if (!queryStr.contains("mockup")) jQuery.ajax(js.Dynamic.literal(
                        url = queryStr,
                        data = stringify(o),
                        contentType = "application/json",
                        success = { (data: js.Any, textStatus: js.Any, jqXHR: JQueryXHR) => {
                          jQuery("#" + idd + "switch").attr("data-switch", bb.toString)
                          click("savecomplete")
                        } },
                        error = { (jqXHR: JQueryXHR, textStatus: js.Any, errorThrow: js.Any) => click("saveerror") },
                        `type` = "PUT"
                      ).asInstanceOf[JQueryAjaxSettings]) else println("using mockup data")
                    })
                  }
                  div(cls := getClass(cn._2), id := idd,
                    onsSwitch(id := idd + "switch", if (fs) checked := "", attr("data-switch") := fs.toString)
                  )
                } else if (cn._1.formats.dropdownId.isDefined) {
                  val mm = Try(dropdowns(cn._1.formats.dropdown.get.url)).getOrElse(Map[Int, String]())
                  div(cls := getClass(cn._2), id := idd, attr("data-formats") := Try("dropdown," + cn._1.formats.dropdown.get.url).getOrElse(""), mm(e.extractV(cn._1.exp.td(0).name).asInstanceOf[Int]))
                } else {
                  if (lastFormat.equals("MM/DD/YYYY") && Try(Moment(e.extractV(cn._1.exp.td(0).name).asInstanceOf[String], cn._1.formats.format.get, true).isValid()).getOrElse(false)) lastFormat = cn._1.formats.format.get
                  div(cls := getClass(cn._2), id := idd, attr("data-formats") := Try("dropdown," + cn._1.formats.dropdown.get.url).getOrElse(""), getFormat(e, cn._1))
                }
              }
              case _ => div()
            }
          }).getOrElse(div()))
          if (panels.isDefined && panels.get.size > 0) {
            val (vidWidth, vidHeight) = (300, 150)
            jQuery(document).on("click", "#" + rowId, (e: JQueryEventObject) => {
              jQuery("#mainpanel").empty()
              val listId = UUID.uuid.replaceAll("-","")
              panels.get.filter(_.query.isDefined).foreach(pan => queryServer(queryFormat(pan.query.get, "order=desc"), (data: js.Any) => {
                val l = data.asInstanceOf[js.Array[js.Dynamic]].toList
                def moveToNext {
                  val current = document.querySelector("ons-carousel").asInstanceOf[js.Dynamic].getActiveIndex().asInstanceOf[Int]
                  if (current + 1 < panels.get.size) {
                    document.querySelector("ons-carousel").asInstanceOf[js.Dynamic].next()
                    def accept(c: html.Canvas, s: String) {
                      val durl = c.toDataURL("image/png")
                      val id = panels.get.zipWithIndex.filter(p => Try(p._1.signature.get.typ.equals(s)).getOrElse(false))(0)._1.signature.get.id
                      println("id: " + id + ", data: " + durl)
                      mobileContext ++= Map(id -> durl)
                      moveToNext
                    }
                    if (panels.get.zipWithIndex.filter(p => Try(p._1.signature.get.typ.equals("signature")).getOrElse(false)).map(_._2).contains(current + 1)) setTimeout(500) {
                      //jQuery("#maincarousel").removeAttr("swipeable")
                      val canvas = jQuery("#" + listId + "sig").get(0).asInstanceOf[html.Canvas]
                      val sig = new SignaturePad(canvas)
                      //jQuery("#" + listId + "sig").css("zIndex", 9999);
                      jQuery(document).on("mouseover", "#" + listId + "sig", (ev: JQueryEventObject) => jQuery("#maincarousel").removeAttr("swipeable"))
                      jQuery(document).on("mouseleave", "#" + listId + "sig", (ev: JQueryEventObject) => jQuery("#maincarousel").attr("swipeable",""))
                      //jQuery(document).on("click", "#" + listId + "sig", (ev: JQueryEventObject) => jQuery("#maincarousel").removeAttr("swipeable"))
                      //jQuery(document).on("touchstart", "#" + listId + "sig", (ev: JQueryEventObject) => ev.stopPropagation())
                      //jQuery(document).on("touchend", "#" + listId + "sig", (ev: JQueryEventObject) => ev.stopPropagation())
                      //jQuery(document).on("touchcancel", "#" + listId + "sig", (ev: JQueryEventObject) => ev.stopPropagation())
                      //jQuery(document).on("touchmove", "#" + listId + "sig", (ev: JQueryEventObject) => ev.stopPropagation())
                      jQuery(document).on("click", "#" + listId + "erase", (ev: JQueryEventObject) => sig.clear())
                      jQuery(document).on("click", "#" + listId + "acceptsignature", (ev: JQueryEventObject) => accept(canvas, "signature"))
                    } else if (panels.get.zipWithIndex.filter(p => Try(p._1.signature.get.typ.equals("camera")).getOrElse(false)).map(_._2).contains(current + 1)) setTimeout(500) {
                      val video = jQuery("#" + listId + "inputcam").get(0).asInstanceOf[js.Dynamic]
                      Try(g.navigator.mediaDevices.getUserMedia(js.Dynamic.literal(
                        video = js.Dynamic.literal(
                          width = js.Dynamic.literal(
                            exact = vidWidth
                          ),
                          height = js.Dynamic.literal(
                            exact = vidHeight
                          )
                      ))).then((stream: js.Any) => {
                          println("Trying to get video stream.")
                          video.srcObject = stream
                          video.play
                        })
                      )
                      val canvas = jQuery("#" + listId + "cam").get(0).asInstanceOf[html.Canvas]
                      jQuery(document).on("click", "#" + listId + "take", (ev: JQueryEventObject) => {
                        println("Camera size is " + video.videoWidth.asInstanceOf[Int] + ", " + video.videoHeight.asInstanceOf[Int])
                        canvas.getContext("2d").drawImage(video, (video.videoWidth.asInstanceOf[Int] - vidWidth) / 2, (video.videoHeight.asInstanceOf[Int] - vidHeight) / 2, vidWidth, vidHeight)
                      })
                      jQuery(document).on("click", "#" + listId + "acceptimage", (ev: JQueryEventObject) => accept(canvas, "camera"))
                    }
                  } else if (goto.isDefined) {
                    if (goto.get(0).url.isDefined) queryServer(queryFormat(goto.get(0).url.get, stringify(mobileContext.toJSDictionary)), (data: js.Any) => {
                      replacePanel(goto.get(0).name)
                    }, goto.get(0).url.get.typ, goto.get(0).url.get.useBody)
                    else Try({
                      replacePanel(goto.get(0).name)
                    })
                  }
                }
                def getItems(s: String = "") = for (e <- l if s.isEmpty || stringify(e).toLowerCase.contains(s.toLowerCase)) yield {
                  val rowIdd = UUID.uuid.replaceAll("-","")
                  jQuery(document).on("click", "#" + rowIdd, (ev: JQueryEventObject) => {
                    val root = ev.target.asInstanceOf[org.scalajs.dom.raw.Element].parentNode.asInstanceOf[org.scalajs.dom.raw.Element]
                    val obj = JSON.parse(root.attributes.getNamedItem("data-object").value)
                    println(stringify(obj))
                    mobileContext ++= pan.query.get.params.get.filter(_.typ.equals("value")).foldLeft(Map[String,Any]())((m, cn) => m ++ Map(cn.alias -> (if (cn.exp.td(0).name.equals("timestamp")) {
                        val now = Moment().unix() * 1000L
                        if (cn.formats.format.isDefined) Moment(now).local().format(cn.formats.format.get) else Moment(now).local().format()
                      } else if (cn.formats.format.isDefined && cn.formats.format.get.equals("Boolean")) {
                        obj.extractV(cn.exp.td(0).name).asInstanceOf[Any]
                      } else if (cn.formats.dropdownId.isDefined) {
                        val mm = Try(dropdowns(cn.formats.dropdown.get.url)).getOrElse(Map[Int, String]())
                        mm(obj.extractV(cn.exp.td(0).name).asInstanceOf[Int]).asInstanceOf[Any]
                      } else getFormat(obj, cn).asInstanceOf[Any])))
                    println(stringify(mobileContext.toJSDictionary))
                    moveToNext
                  })
                  getListItem(e, rowIdd, pan.query.get.params.get)
                }
                jQuery("#" + listId + pan.title).append(getItems().render)
                jQuery("#" + listId + pan.title + "search").change((ev: JQueryEventObject) => {
                  val filter = jQuery("#" + listId + pan.title + "search").value.asInstanceOf[String]
                  jQuery("#" + listId + pan.title).find(".list-item").slice(2).remove()
                  jQuery("#" + listId + pan.title).append(getItems(filter).render)
                })
              }))
              Try(jQuery("#mainpanel").append(onsCarousel(id := "maincarousel", fullscreen := "", /*if (panels.get.filter(p => Try(p.signature.get.typ.equals("signature")).getOrElse(false)).size == 0)*/ swipeable := "", autoscroll := "", overscrollable := "",
                for (pan <- panels.get) yield onsCarouselItem(
                  if (pan.query.isDefined)
                      onsList(id := listId + pan.title,
                        onsListHeader(pan.title),
                        p(cls :="search-bar", style := "text-align: center; margin-top: 10px;",
                          onsSearchInput(id := listId + pan.title + "search", placeholder := "Search " + pan.title))
                      )
                  else if (pan.signature.isDefined) {
                    setTimeout(1000) {
                          jQuery("#" + listId + "sig").unbind("swipe")
                          jQuery(document).on("swipe", "#" + listId + "sig", (ev: JQueryEventObject) => e.stopPropagation)
                    }
                    div(style := "text-align: center; font-size: 30px; margin-top: 10px; color: #000; background-color: black;",
                      if (pan.signature.get.typ.equals("signature"))
                        onsList(onsListHeader(pan.title),
                          canvas(id := listId + "sig", cls := "center", style := "background-color: lightGrey;", width := /*(window.innerWidth / 2) +*/ "300px", height := /*(window.innerHeight / 2) +*/ "150px"),
                          onsListItem(onsButton(id := listId + "erase", modifier := "large", "Erase")),
                          onsListItem(onsButton(id := listId + "acceptsignature", modifier := "large", "Accept")))
                      else onsList(onsListHeader(pan.title),
                        video(id := listId + "inputcam", cls := "center", autoplay := "", style := "background-color: lightGreen;", width := vidWidth + "px", height := vidHeight + "px"),
                        canvas(id := listId + "cam", cls := "center", style := "background-color: lightGrey;", width := vidWidth + "px", height := vidHeight + "px"),
                        onsListItem(onsButton(id := listId + "take", modifier := "large", "Take")),
                        onsListItem(onsButton(id := listId + "acceptimage", modifier := "large", "Accept")))
                    )
                  }
                  else div(style := "text-align: center; font-size: 30px; margin-top: 20px; color: #000;", pan.title)
                )
              ).render))
            })
          }
          getListItem(e, rowId, cols)
        }
        jQuery("#" + s.asId + "list").append(
          onsList(
            onsListHeader(s),
            for (e <- l) yield getCols(e, cols)
          ).render
        )
      } else {
        def getCols(e: js.Dynamic, cols: List[Param]) = {
          val rowId = UUID.uuid.replaceAll("-","")
          cols.filter(!_.typ.equals("add")).foldLeft(List[JsDom.TypedTag[org.scalajs.dom.raw.HTMLElement]]())((l, cn) => l ++ List(Try(
            cn.typ match {
              case "column" => {
                if (cn.formats.format.isDefined && cn.formats.format.get.equals("Boolean")) {
                  val fs = e.extractV(cn.exp.td(0).name).asInstanceOf[Boolean]
                  td(id := rowId + cn.exp.td(0).name.asId, attr("data-format") := Try(cn.formats.format.get).getOrElse(""), div(cls := "form-check disabled",
                    label(cls := "form-check-label",
                      input(id := rowId + cn.exp.td(0).name.asId + "check", cls := "form-check-input", `type` := "checkbox", if (fs) `checked` := ""),
                      span(cls := "form-check-sign"))))
                } else if (cn.formats.dropdownId.isDefined) {
                  val mm = Try(dropdowns(cn.formats.dropdown.get.url)).getOrElse(Map[Int, String]())
                  td(id := rowId + cn.exp.td(0).name.asId, attr("data-format") := Try("dropdown," + cn.formats.dropdown.get.url).getOrElse(""), Try(mm(e.extractV(cn.exp.td(0).name).asInstanceOf[Int])).getOrElse("").asInstanceOf[String])
                } else {
                  if (lastFormat.equals("MM/DD/YYYY") && Try(Moment(e.extractV(cn.exp.td(0).name).asInstanceOf[String], cn.formats.format.get, true).isValid()).getOrElse(false)) lastFormat = cn.formats.format.get
                  td(id := rowId + cn.exp.td(0).name.asId, attr("data-format") := Try(cn.formats.format.get).getOrElse(""), getFormat(e, cn))
                }
              }
              case "media" => td(cls := "text-center", a(target := "_blank", href := extractMapOrUser("mediaPath") + "/" + e.extractV(cn.exp.td(0).name).asInstanceOf[String], cls := "btn btn-round btn-info btn-icon btn-sm like", i(cls := "now-ui-icons education_paper")))
              case "edit" => td(cls := "text-center", a(href := "#editobject", id := rowId, attr("data-query") := processQueryStr(queryStr, useBody)._1, attr("data-object") := stringify(e), cls := "btn btn-round btn-info btn-icon btn-sm like", i(cls := "now-ui-icons files_paper")), if (cn.delete) a(href := "#editdeleteobject", id := rowId + "delete", attr("data-query") := processQueryStr(queryStr, useBody)._1, attr("data-object") := stringify(e), cls := "btn btn-round btn-info btn-icon btn-sm like", i(cls := "now-ui-icons ui-1_simple-remove")))
            }
          ).getOrElse(td(""))))
        }
        def expBtn(t: String) = js.Dynamic.literal(
          extend = t,
          title = Try(titl.get).getOrElse("").asInstanceOf[js.Dynamic],
          messageTop = Try(message.get).getOrElse("").asInstanceOf[js.Dynamic],
          messageBottom = Try(footer.get).getOrElse("").asInstanceOf[js.Dynamic],
          exportOptions = js.Dynamic.literal(
            columns = ":visible"
          )
        )
        val btns = Seq(
          "copy",
          expBtn("csv"),
          expBtn("excel"),
          expBtn("pdf"),
          expBtn("print")
        ).toJSArray
        if (l.length > 0) {
          jQuery("#" + s.asId + "datatable").addClass("table-striped")
          jQuery("#" + s.asId + "datatable").append(thead(cls := "text-primary", tr(for (cn <- cols if !cn.typ.equals("add")) yield th(cn.alias))).render)
          jQuery("#" + s.asId + "datatable").append(tbody(id := s.asId + "datatablebodytr", for (e <- l) yield tr(for (t <- getCols(e, cols)) yield t)).render)
          jQuery("#" + s.asId + "datatable").DataTable(js.Dynamic.literal(
            pagingType = "full_numbers",
            scrollX = true,
            scrollY = window.innerHeight / 2,
            order = Seq().toJSArray,
            lengthMenu = Seq(
              Seq(numItems, numItems * 2, numItems * 4, -1).toJSArray,
              Seq(numItems, numItems * 2, numItems * 4, "All").toJSArray
            ).toJSArray,
            responsive = true,
            language = js.Dynamic.literal(
              search =  "_INPUT_",
              searchPlaceholder ="Search records"
            ),
            dom = "Bfrtip",
            buttons = btns
          ))
        } else {
          def addToTable(obj: js.Dynamic, colList: List[Param]): (List[String],List[List[JsDom.TypedTag[org.scalajs.dom.raw.HTMLElement]]]) = {
            val groupcols = colList.filter(c => !c.exp.td(0).name.contains("."))
            val gs = table(border := "1", tbody(tr(for (cn <- groupcols) yield Try(
              cn.typ match {
                case "column" => {
                  if (lastFormat.equals("MM/DD/YYYY") && Try(Moment(obj.extractV(cn.exp.td(0).name).asInstanceOf[String], cn.formats.format.get, true).isValid()).getOrElse(false)) lastFormat = cn.formats.format.get
                  td(cn.alias + ": " + getFormat(obj, cn))
                }
                case _ => td()
              }
            ).getOrElse(td()))))
            val array = colList.filter(c => c.exp.td(0).name.contains(".")).map(c => c.exp.td(0).name.split("\\.").head)
            if (array.length > 0) {
              val l = obj.extractV(array(0)).asInstanceOf[js.Array[js.Dynamic]].toList
              val newcols = colList.filter(c => c.exp.td(0).name.contains(".")).map(c => c.copy(exp = Expr(List(TypeDef(c.exp.td(0).name.split("\\.").tail.mkString("."), c.exp.td(0).alias, c.exp.td(0).typ, c.exp.td(0).formats, c.exp.td(0).multi, c.exp.td(0).required)), c.exp.op)))
              l.foldLeft((List[String](),List[List[JsDom.TypedTag[org.scalajs.dom.raw.HTMLElement]]]()))((l, o) => {
                val l2 = addToTable(o, newcols)
                (List("") ++ l2._1, l._2 ++ l2._2.foldLeft(List[List[JsDom.TypedTag[org.scalajs.dom.raw.HTMLElement]]]())((l, e) => l ++ List(List(td(gs)) ++ e)))
              })
            } else (colList.foldLeft(List[String]())((l, c) => l ++ List(c.alias)), List(getCols(obj, colList)))
          }
          val lll = addToTable(data.asInstanceOf[js.Dynamic], cols)
          val hidden = lll._1.filter(_.isEmpty).length
          jQuery("#" + s.asId + "datatable").append(thead(cls := "text-primary", tr(for (s <- lll._1) yield if (s.isEmpty) th(cls := "none") else th(s))).render)
          jQuery("#" + s.asId + "datatable").append(tbody(for (ll <- lll._2) yield tr(for (e <- ll) yield e)).render)
          jQuery("#" + s.asId + "datatable").DataTable(js.Dynamic.literal(
            pagingType = "full_numbers",
            scrollX = true,
            scrollY = window.innerHeight / 2,
            order = Seq().toJSArray,
            lengthMenu = Seq(
              Seq(numItems, numItems * 2, numItems * 4, -1).toJSArray,
              Seq(numItems, numItems * 2, numItems * 4, "All").toJSArray
            ).toJSArray,
            responsive = true,
            language = js.Dynamic.literal(
              search =  "_INPUT_",
              searchPlaceholder ="Search records"
            ),
            dom = "Bfrtip",
            buttons = btns,
            columnDefs = Seq(
              js.Dynamic.literal(
                targets = (0 until hidden).toJSArray,
                visible = false
              )
            ).toJSArray,
            rowGroup = js.Dynamic.literal(
              dataSrc = (0 until hidden).toJSArray
            )
          ))
        }
        if (cols.filter(_.typ.equals("add")).length > 0) Try(jQuery("#" + s.asId + "datatabletitle").parent().append(a(href := "#addobject", id := "addobjectbtn",
            attr("data-query") := processQueryStr(queryStr, useBody)._1, cls := "btn btn-round btn-info btn-icon btn-sm like", i(cls := "now-ui-icons ui-1_simple-add")).render))
        if (!fromSetDate.isEmpty) {
          jQuery("#" + s.asId + "datatable").append(caption(style := js.Dictionary("caption-side" -> "top"), Moment(fromSetDate).format(lastFormat) + " - " + Moment(toSetDate).format(lastFormat)).render)
          jQuery("#" + s.asId + "datatabletitle").text(s + " - " + Moment(fromSetDate).format(lastFormat) + " - " + Moment(toSetDate).format(lastFormat))
        } else jQuery("#" + s.asId + "datatabletitle").text(s)
        fromSetDate = ""
        toSetDate = ""
        //jQuery("#" + s.asId + "datatable").on("draw.dt", (e: JQueryEventObject) => logic)
      }
      //logic
    }, typ.toUpperCase, useBody)
    if (mobileSite) onsPage(id := s.asId, p(cls :="search-bar", style := "text-align: center; margin-top: 10px;", onsSearchInput(placeholder := "Search")), div(id := s.asId + "list", cls := "after-search-bar")) else {
      dialog("savecomplete", "Save complete", "Add/Edit is done!")
      dialog("saveerror", "Save error", "Problem saving!")
      dialog("genericerror", "Error", "An error occured.")
      div(
        id := s.asId,
        cls := "content",
        div(cls := "modal fade", id := "multiwindow", tabindex := "1", role := "dialog", attr("aria-labelledby") := "Multi", attr("aria-hidden") := "true",  style := js.Dictionary("z-index" -> "1000"),
          div(
            cls := "modal-dialog modal-lg",
            div(
              cls := "modal-content",
              div(
                cls := "modal-header justify-content-center",
                button(`type` := "button", cls := "close", attr("data-dismiss") := "modal", attr("aria-hidden") := "true",
                  i(cls := "now-ui-icons ui-1_simple-remove")),
                h4(cls := "title title-up", "Add")),
              div(
                cls := "modal-body",
                div(id := "multibody", cls := "card-body")),
              div(
                cls := "modal-footer",
                a(href := "#savemultiadd", cls := "btn btn-info", attr("data-dismiss") := "modal", "Save"),
                a(href := "#closemulti", cls := "btn btn-danger", attr("data-dismiss") := "modal", "Close"))))),
        div(cls := "modal fade", id := "multiwindowedit", tabindex := "1", role := "dialog", attr("aria-labelledby") := "Multi", attr("aria-hidden") := "true",  style := js.Dictionary("z-index" -> "1000"),
          div(
            cls := "modal-dialog modal-lg",
            div(
              cls := "modal-content",
              div(
                cls := "modal-header justify-content-center",
                button(`type` := "button", cls := "close", attr("data-dismiss") := "modal", attr("aria-hidden") := "true",
                  i(cls := "now-ui-icons ui-1_simple-remove")),
                h4(cls := "title title-up", "Edit")),
              div(
                cls := "modal-body",
                div(id := "multibodyedit", cls := "card-body")),
              div(
                cls := "modal-footer",
                a(href := "#savemultiedit", cls := "btn btn-info", attr("data-dismiss") := "modal", "Save"),
                a(href := "#closemulti", cls := "btn btn-danger", attr("data-dismiss") := "modal", "Close"))))),
        div(cls := "modal fade", id := "addobjectwindow", tabindex := "-1", role := "dialog", attr("aria-labelledby") := "Add", attr("aria-hidden") := "true",
          div(
            cls := "modal-dialog modal-lg",
            div(
              cls := "modal-content",
              div(
                cls := "modal-header justify-content-center",
                button(`type` := "button", cls := "close", attr("data-dismiss") := "modal", attr("aria-hidden") := "true",
                  i(cls := "now-ui-icons ui-1_simple-remove")),
                h4(cls := "title title-up", "Add")),
              div(
                cls := "modal-body",
                div(id := "addbody", cls := "card-body")),
              div(
                cls := "modal-footer",
                a(href := "#saveadd", cls := "btn btn-info", attr("data-dismiss") := "modal", "Save"),
                button(`type` := "button", cls := "btn btn-danger", attr("data-dismiss") := "modal", "Close"))))),
        div(cls := "modal fade", id := "editobjectwindow", tabindex := "-1", role := "dialog", attr("aria-labelledby") := "Edit", attr("aria-hidden") := "true",
          div(
            cls := "modal-dialog modal-lg",
            div(
              cls := "modal-content",
              div(
                cls := "modal-header justify-content-center",
                button(`type` := "button", cls := "close", attr("data-dismiss") := "modal", attr("aria-hidden") := "true",
                  i(cls := "now-ui-icons ui-1_simple-remove")),
                h4(cls := "title title-up", "Edit")),
              div(
                cls := "modal-body",
                div(id := "editbody", cls := "card-body")),
              div(
                cls := "modal-footer",
                a(href := "#saveedit", cls := "btn btn-info", attr("data-dismiss") := "modal", "Save"),
                button(`type` := "button", cls := "btn btn-danger", attr("data-dismiss") := "modal", "Close"))))),
        div(
          cls := "row",
          div(
            cls := "col-md-12",
            div(
              cls := "card",
              div(
                cls := "card-header",
                h4(id := s.asId + "datatabletitle", cls := "card-title", s)),
              div(
                cls := "card-body",
                div(
                  cls := "table-responsive",
                  table(id := s.asId + "datatable", cls := "display", width := "100%")))))))
    }
  }

  var searchValue = ""
  var searchablePanel: Option[String] = None
  var currRoot: Node = _
  var currSelected: Node = _
  var currPanel: String = _

  def replacePanel(p: String, force: Boolean = false) = if ((force || !p.equals(currPanel)) && (!p.equals("dashboard") || "dashboard".notPresent)) {
    println("loading " + p)
  	currPanel = p
    pages.filterKeys(k => (force || !k.equals(p)) && !k.equals("main")).foreach(k => jQuery("#" + k._1.asId).remove())
    if ("mainpanel".notPresent) {
      g.document.body.appendChild(pages(p)().render)
      jQuery("#mainsearch").keyup((Event: JQueryEventObject, ui: js.Dynamic) => {
        Try(clearTimeout(timer))
        timer = setTimeout(1000) {
          searchValue = jQuery("#mainsearch").value.asInstanceOf[String]
          if (searchablePanel.isDefined) {
            pages.filterKeys(k => !k.equals("main")).foreach(k => jQuery("#" + k._1).remove())
            Try(jQuery("#mainpanel").append(pages(searchablePanel.get)().render))
            //logic
          }
        }
      })
    } else {
      Try(jQuery("#mainpanel").append(pages(p)().render))
      //logic
    }
	setTimeout(500) {
		resetEvents
	}
  }

  var editObj: js.Dynamic = _
  var editQuery = ""
  var editObjMulti: js.Dynamic = _
  var multiRef = ""
  var arrayNameMulti = ""
  var idxMulti = 0
  var editRow = ""
  var fromSetDate = ""
  var toSetDate = ""
  var lastDialog = ""

  def reloadAll(s: String) {
    name = ""
    pages = Map[String, () => scalatags.JsDom.TypedTag[org.scalajs.dom.html.Element]]()
    transitions = List[Context]()
    menu = List[(String, String, Option[List[String]])]()
    submenu = List[String]()
    currentElement = ""
    menus = ("", "")
    selections = Map[String,String]()
    currentContext = None
    jQuery("body").empty()
    val result = parseAll(website, s)
      result match {
        case Success(x, _) => println("Successful parsing of script.")
        case NoSuccess(err, next) => println("failed to parse script " + "(line " + next.pos.line + ", column " + next.pos.column + "):\n" + err + "\n" + next.pos.longString)
    }
  }

  def loadDropdowns(l: Iterable[Formats]) = l.foreach(v => Try({
    dropdowns += v.dropdown.get.url -> Map()
    queryServer(queryFormat(Query(v.dropdown.get.url, false, "get", v.dropdown.get.extras, None, None), ""), (data: js.Any) => {
      val l = data.asInstanceOf[js.Array[js.Dynamic]].toList
      println("dropdown blank is " + v.dropdown.get.url + ", " + v.dropdown.get.blank)
      val m = (if (v.dropdown.get.blank) Map(-1 -> "none") else Map[Int,String]()) ++ l.map(o => Try(Some((o.extractV(v.dropdownId.get).asInstanceOf[Int], o.extractV(v.format.get).asInstanceOf[String].replaceAll("  "," ")))).getOrElse(None)).flatten.toMap
      println("Dropdown loaded: " + m)
      dropdowns += v.dropdown.get.url -> m
    })
  }))

  def getRawProp(e: org.scalajs.dom.raw.Element, p: String) = e.attributes.getNamedItem(p).value.replaceAll("#","")

  def getProp(e: dom.Event, p: String) = Try(getRawProp(e.target.asInstanceOf[org.scalajs.dom.raw.Element], p)).
    getOrElse(Try(getRawProp(e.target.asInstanceOf[org.scalajs.dom.raw.Element].parentNode.asInstanceOf[org.scalajs.dom.raw.Element], p)).
      getOrElse(Try(getRawProp(e.target.asInstanceOf[org.scalajs.dom.raw.Element].parentNode.asInstanceOf[org.scalajs.dom.raw.Element].parentNode.asInstanceOf[org.scalajs.dom.raw.Element], p)).
        getOrElse(Try(getRawProp(e.target.asInstanceOf[org.scalajs.dom.raw.Element].parentNode.asInstanceOf[org.scalajs.dom.raw.Element].parentNode.asInstanceOf[org.scalajs.dom.raw.Element].parentNode.asInstanceOf[org.scalajs.dom.raw.Element], p)).getOrElse(""))))

  def resetEvents = {
	  List("a", "ons-button", "ons-list-item", "ons-tab", "ons-toolbar-button").foreach(s => {
		println("Binding click to " + s)
		jQuery(s).unbind("click")
		jQuery(s).click((e: dom.Event) => processEvents(e))
	})
	List("ons-input", "canvas").foreach(s => {
		println("Binding blur to " + s)
		jQuery(s).unbind("blur")
		jQuery(s).blur((e: dom.Event) => processEvents(e))
	})
	List("ons-input", "canvas").foreach(s => {
		println("Binding focus to " + s)
		jQuery(s).unbind("focus")
		jQuery(s).focus((e: dom.Event) => processEvents(e))
	})
	List("canvas").foreach(s => {
		println("Binding dblclick to " + s)
		jQuery(s).unbind("dblclick")
		jQuery(s).dblclick((e: dom.Event) => processEvents(e))
	})
	List("canvas").foreach(s => {
		println("Binding mouseover to " + s)
		jQuery(s).unbind("mouseover")
		jQuery(s).mouseover((e: dom.Event) => processEvents(e))
	})
	List("canvas").foreach(s => {
		println("Binding mouseleave to " + s)
		jQuery(s).unbind("mouseleave")
		jQuery(s).mouseleave((e: dom.Event) => processEvents(e))
	})
	List("canvas").foreach(s => {
		println("Binding touchstart to " + s)
		jQuery(s).unbind("touchstart")
		jQuery(s).mouseover((e: dom.Event) => processEvents(e))
	})
	List("canvas").foreach(s => {
		println("Binding touchend to " + s)
		jQuery(s).unbind("touchend")
		jQuery(s).mouseleave((e: dom.Event) => processEvents(e))
	})
  }

  def transit(t: Context) = {
    currentContext = Some(t)
    if (jQuery("#rememberme").prop("checked").asInstanceOf[Boolean]) {
      val str = t.tr.url.get.inparams.get.map(v => v.inputField + "=" + (if (v.fixed) v.inputField else jQuery("#" + v.inputField).value.toString)).mkString(",")
      println("Previous: " + str)
      store.setItem("rememberme", str)
    } else store.removeItem("rememberme")
	def doTransition = if (scriptVal.isDefined && (scriptVal.get.key.isEmpty || !currentContext.get.response.extractV(scriptVal.get.key.get).asInstanceOf[String].isEmpty)) {
		println("Loading " + scriptVal.get.url)
		queryServer(scriptVal.get.url + (if (scriptVal.get.key.isEmpty) "" else currentContext.get.response.extractV(scriptVal.get.key.get).asInstanceOf[String]), (data: js.Any) => {
			val str = if (scriptVal.get.url.endsWith("txt")) data.asInstanceOf[String] else {
				val obj = data.asInstanceOf[js.Array[js.Dynamic]].toList.head
				obj.script.asInstanceOf[String]
			}
			println("Loaded script: " + str)
			val localScr = Try(store.getItem("script")).getOrElse("")
			if (!localScr.equals(str)) {
				scriptDef = str
				store.setItem("script", str)
				click("loginscript")
				setTimeout(3000) {
					reloadAll(str)
				}
			} else replacePanel(t.tr.name)
		})
	} else replacePanel(t.tr.name)
    if (t.tr.url.isDefined) {
      val mm = t.tr.url.get.inparams.get.map(v => (v.field, if (v.fixed) v.inputField else if (v.sha256) jQuery("#" + v.inputField).value.toString.hex else jQuery("#" + v.inputField).value.toString)).toMap
      val req = mm.toJSDictionary.asInstanceOf[js.Dynamic]
      println("clicked " + t + " with " + stringify(req))
      val mainUrl = t.tr.url.get.url + (if (t.tr.url.get.url.contains("?") || t.tr.url.get.typ.toLowerCase.equals("get")) encodeURI(stringify(req)) else "")
      if (t.tr.url.get.url.contains("mockup")) {
		  println("We are using a mockup transition")
		  doTransition
	  } else jQuery.ajax(js.Dynamic.literal(
          url = t.tr.url.get.url + (if (t.tr.url.get.url.contains("?") || t.tr.url.get.typ.toLowerCase.equals("get")) encodeURI(stringify(req)) else ""),
          data = if (t.tr.url.get.typ.toLowerCase.equals("post")) stringify(req) else "",
          contentType = "application/json",
          success = { (data: js.Any, textStatus: js.Any, jqXHR: JQueryXHR) => {
            val array = Try(data.asInstanceOf[js.Array[js.Dynamic]].toList)
            if (array.isSuccess && array.get.length > 0) {
              currentContext = Some(t.copy(response = array.get(0)))
              loadDropdowns(dropdownsLoad.values)
			  doTransition
            } else {
              val obj = Try(data.asInstanceOf[js.Dynamic])
              if (obj.isSuccess && (obj.get.valid.asInstanceOf[Boolean] || obj.get.status.asInstanceOf[Boolean])) {
                currentContext = Some(t.copy(response = obj.get))
                replacePanel(t.tr.name)
              } else click("loginfailure")
            }
          }},
          error = { (jqXHR: JQueryXHR, textStatus: js.Any, errorThrow: js.Any) => processError(jqXHR, textStatus, errorThrow) },
          `type` = if (t.tr.url.get.typ.toLowerCase.equals("post")) "POST" else "GET"
        ).asInstanceOf[JQueryAjaxSettings])
    } else {
        println("Direct transition to " + t.tr.name)
        doTransition
    }
  }

  def processEvents(e: dom.Event) {
    if (!mobileSite && !"mainpanel".notPresent) {
      Try(jQuery(currRoot).removeClass("text-dark"))
      Try(jQuery(currSelected).removeClass("active"))
      Try({
        currRoot = e.target.asInstanceOf[org.scalajs.dom.raw.Element]
        jQuery(currRoot).addClass("text-dark")
        currSelected = currRoot.parentNode.parentNode
        jQuery(currSelected).addClass("active")
      })
    }
    val hr = if (mobileSite) getProp(e, "id") else getProp(e, "href")
    val targetInd = getProp(e, "target")
    val data = getProp(e, "data-info")
    val dataStore = getProp(e, "data-store")
    println("Event " + e.`type` + ", " + e.target.asInstanceOf[org.scalajs.dom.raw.Element].outerHTML + ", " + hr + ", " + targetInd + ", " + data + ", " + dataStore)
    def allfields(cols: List[TypeDef], editObj: Option[js.Dynamic]) = for (v <- cols) yield {
      val absent = !editObj.isDefined || editObj.get.extractV(v.name) == null
      if (v.typ.equals("Boolean")) div(cls := "form-group has-label",
        label(v.alias + (if (v.required) "*" else "")), div(cls := "form-check form-check-inline",
          label(cls := "form-check-label",
            input(id := "edit" + v.name.asId, cls := "form-check-input", `type` := "checkbox", if (!absent && editObj.get.extractV(v.name).asInstanceOf[Boolean]) `checked` := ""),
            span(cls := "form-check-sign"))))
        else if (v.formats.dropdownId.isDefined){
          val mm = dropdowns(v.formats.dropdown.get.url)
          println(mm)
          val idd = Try(math.max(editObj.get.extractV(v.name).asInstanceOf[Int], mm.keys.toList.sorted.head)).getOrElse(mm.keys.toList.sorted.head)
          div(div(cls := "form-group has-label",
            label(v.alias + (if (v.required) "*" else ""))),
            select(id := "dropdown" + v.name.asId, cls := "form-control form-check-label", attr("data-live-search") := "true", attr("data-style") := "btn-info", attr("title") := Try(mm(idd)).getOrElse(mm(mm.keys.toList.sorted.head)),
              for (vv <- mm.toList.sortBy(_._1)) yield option(attr("data-live-search") := vv._2, id := "dropdown" + v.name.asId + vv._2.asId, vv._2)))
          } else if (v.multi.isDefined) {
          div(cls := "form-group has-label",
            label(v.alias + (if (v.required) "*" else "")),
            div(cls := "table-full-width table-responsive",
            a(href := "#getmulti" + (if (editObj.isDefined) "edit" else "add"), id := "getmulti" + v.name.asId, cls := "btn btn-round btn-info btn-icon btn-sm like", i(cls := "now-ui-icons ui-1_simple-add")),
            table(cls := "table",
              tbody(id := "getmultitable",
                  for (o <- editObj.get.extractV(v.name).asInstanceOf[js.Array[js.Dynamic]].toList) yield {
                    val mainRef = UUID.uuid.replaceAll("-","")
                    println(stringify(o) + ", " + v.multi.get)
                    tr(
                      id := "multientry" + mainRef,
                      for (pp <- v.multi.get.td) yield if (o.extractV(pp.name).asInstanceOf[String].equals("true") || o.extractV(pp.name).asInstanceOf[String].equals("false")) td(
                        div(cls := "form-check",
                          label(cls := "form-check-label",
                          input(cls := "form-check-input", `type` := "checkbox", if (o.extractV(pp.name).asInstanceOf[Boolean]) checked := ""),
                          span(cls := "form-check-sign")
                          )
                        )
                      ) else if (pp.formats.dropdownId.isDefined) {
                        val mm = Try(dropdowns(pp.formats.dropdown.get.url)).getOrElse(Map[Int, String]())
                        td(cls := "text-left", Try(mm(o.extractV(pp.name).asInstanceOf[Int])).getOrElse("Not found").asInstanceOf[String])
                      } else td(cls := "text-left", o.extractV(pp.name).asInstanceOf[String]),
                      td(cls := "td-actions text-right",
                        a(href := "#" + v.name + "multiedit" + mainRef, rel := "tooltip", attr("data-object") := stringify(o), cls := "btn btn-info btn-round btn-icon btn-icon-mini btn-neutral", attr("data-original-title") := "Edit Task",
                          i(cls := "now-ui-icons ui-2_settings-90")
                        ),
                        a(href := "#" + v.name + "multidelete" + mainRef, rel := "tooltip", cls := "btn btn-danger btn-round btn-icon btn-icon-mini btn-neutral", attr("data-original-title") := "Remove",
                          i(cls := "now-ui-icons ui-1_simple-remove")
                        )
                      )
                    )
                  }
                )
              )
            )
          )
        } else div(cls := "form-group has-label",
            label(v.alias + (if (v.required) "*" else "")),
            input(id := "edit" + v.name.asId, cls := "form-control", `type` := "text", if (v.required) required := "true", value := (if (absent) "" else editObj.get.extractV(v.name).asInstanceOf[String])))
    }
    def processCol(m: Map[String, js.Dynamic], v: TypeDef) = if (v.multi.isDefined) m else {
      val vv = if (v.formats.dropdownId.isDefined) {
        val mm = dropdowns(v.formats.dropdown.get.url).map(_.swap)
        println("processCols: " + mm)
        val str = jQuery("#" + "dropdown" + v.name.asId).value.asInstanceOf[String].replaceAll("  "," ")
        println("processCols: " + str + ", " + Try(mm(str)).getOrElse(-1).asInstanceOf[js.Dynamic])
        Try(mm(str)).getOrElse(-1).asInstanceOf[js.Dynamic]
      } else if (jQuery("#" + "edit" + v.name.asId).is(":checkbox")) jQuery("#" + "edit" + v.name.asId).is(":checked").asInstanceOf[js.Dynamic] else if (v.typ.equals("Number")) g.parseFloat(jQuery("#" + "edit" + v.name.asId).value) else jQuery("#" + "edit" + v.name.asId).value
      val ar = v.name.split("\\.")
      if (ar.size == 1) Try(if (vv.asInstanceOf[Int] == -1 || m(v.name).asInstanceOf[String].equals(vv)) m else m ++ Map(v.name -> vv)).getOrElse(m ++ Map(v.name -> vv))
      else {
        val m1 = m(ar(0)).asInstanceOf[js.Dictionary[js.Dynamic]].toMap
        val m2 = Try(if (vv.asInstanceOf[Int] == -1 || m1(ar(1)).asInstanceOf[String].equals(vv)) m1 else m1 ++ Map(ar(1) -> vv)).getOrElse(m1 ++ Map(ar(1) -> vv))
        m ++ Map(ar(0) -> m2.toJSDictionary.asInstanceOf[js.Dynamic])
      }
    }
    def repPage(s: String) {
      if (pages.contains(s)) {
        updaters.clear
        replacePanel(s)
      }
    }
    hr match {
      case "mobilemenuclick" => document.getElementById("menumobile").asInstanceOf[js.Dynamic].open()
      case "print" => printme
      case "dates" => click("dateswindow")
      case "edit" => click("editwindow")
      case "reload" => {
        scriptDef = jQuery("#editcontent").value.toString
        reloadAll(scriptDef)
      }
      case "addobject" => {
        jQuery("#addbody").empty()
        jQuery("#addbody").append(allfields(currentCols.filter(_.typ.equals("add")).head.exp.td, None).render)
        editQuery = jQuery("#addobjectbtn").attr("data-query").asInstanceOf[String]
        jQuery("#addbody").append(div(cls := "category form-category","* Required fields").render)
        click("addobjectwindow")
      }
      case "getmultiadd" => {
        lastDialog = "addobjectwindow"
        click("addobjectwindow")
        jQuery("#multibody").empty()
        jQuery("#multibody").append(allfields(currentCols.filter(_.typ.equals("add")).head.exp.td.filter(_.multi.isDefined).head.multi.get.td, None).render)
        jQuery("#multibody").append(div(cls := "category form-category","* Required fields").render)
        click("multiwindow")
      }
      case "getmultiedit" => {
        lastDialog = "editobjectwindow"
        click("editobjectwindow")
        jQuery("#multibody").empty()
        jQuery("#multibody").append(allfields(currentCols.filter(_.typ.equals("edit")).head.exp.td.filter(_.multi.isDefined).head.multi.get.td, None).render)
        jQuery("#multibody").append(div(cls := "category form-category","* Required fields").render)
        click("multiwindow")
      }
      case "editobject" => {
        val root = e.target.asInstanceOf[org.scalajs.dom.raw.Element].parentNode.asInstanceOf[org.scalajs.dom.raw.Element]
        editObj = JSON.parse(root.attributes.getNamedItem("data-object").value)
        println("Editing " + stringify(editObj))
        jQuery("#editbody").empty()
        editRow = root.attributes.getNamedItem("id").value
        jQuery("#editbody").append(allfields(currentCols.filter(_.typ.equals("edit")).head.exp.td, Some(editObj)).render)
        editQuery = root.attributes.getNamedItem("data-query").value
        jQuery("#editbody").append(div(cls := "category form-category","* Required fields").render)
        click("editobjectwindow")
      }
      case "editdeleteobject" => {
        val root = e.target.asInstanceOf[org.scalajs.dom.raw.Element].parentNode.asInstanceOf[org.scalajs.dom.raw.Element]
        editObj = JSON.parse(root.attributes.getNamedItem("data-object").value)
        editRow = root.attributes.getNamedItem("id").value
        editQuery = root.attributes.getNamedItem("data-query").value
        println("Deleting " + stringify(editObj))
        jQuery.ajax(js.Dynamic.literal(
          url = editQuery,
          data = stringify(editObj),
          contentType = "application/json",
          success = { (data: js.Any, textStatus: js.Any, jqXHR: JQueryXHR) => {
              val obj = data.asInstanceOf[js.Dynamic]
              jQuery("#" + editRow).parent().parent().remove()
              loadDropdowns(dropdownsLoad.filterKeys(editQuery.startsWith(_)).values)
              click("savecomplete")
            }
          },
          error = { (jqXHR: JQueryXHR, textStatus: js.Any, errorThrow: js.Any) => click("saveerror") },
          `type` = "DELETE"
        ).asInstanceOf[JQueryAjaxSettings])
      }
      case "saveadd" => {
        val mo = currentCols.filter(_.typ.equals("add")).head.exp.td.foldLeft(Map[String, js.Dynamic]())((m, v) => processCol(m, v))
        import js.JSConverters._
        val o = mo.toJSDictionary.asInstanceOf[js.Dynamic]
        println(stringify(o))
        jQuery.ajax(js.Dynamic.literal(
          url = editQuery,
          data = stringify(o),
          contentType = "application/json",
          success = { (data: js.Any, textStatus: js.Any, jqXHR: JQueryXHR) => {
              val queryStr = jQuery("#addobjectbtn").attr("data-query").asInstanceOf[String]
              val obj = data.asInstanceOf[js.Dynamic].asInstanceOf[js.Dictionary[js.Dynamic]].toMap
              val rowId = UUID.uuid.replaceAll("-","")
              jQuery("#" + currentTable.asId + "datatablebodytr").append(tr(currentCols.filter(c => !c.typ.equals("add") && !c.typ.equals("edit")).map(c => Try({
                if (c.formats.format.isDefined && c.formats.format.get.equals("Boolean")) {
                  val fs = obj(c.exp.td(0).name).asInstanceOf[Boolean]
                  td(id := rowId + c.exp.td(0).name.asId, attr("data-format") := "Boolean", div(cls := "form-check disabled",
                    label(cls := "form-check-label",
                      input(id := rowId + c.exp.td(0).name.asId + "check", cls := "form-check-input", `type` := "checkbox", if (fs) `checked` := ""),
                      span(cls := "form-check-sign"))))
                } else if (c.formats.dropdownId.isDefined) {
                  val mm = Try(dropdowns(c.formats.dropdown.get.url)).getOrElse(Map[Int, String]())
                  td(id := rowId + c.exp.td(0).name.asId, attr("data-format") := Try("dropdown," + c.formats.dropdown.get.url).getOrElse(""), mm(data.asInstanceOf[js.Dynamic].extractV(c.exp.td(0).name).asInstanceOf[Int]))
                } else Try(c.typ match {
                    case "edit" => td(cls := "text-center", a(href := "#editobject", id := rowId, attr("data-query") := queryStr, attr("data-object") := stringify(data.asInstanceOf[js.Dynamic]), cls := "btn btn-round btn-info btn-icon btn-sm like", i(cls := "now-ui-icons files_paper")), if (c.delete) a(href := "#editdeleteobject", id := rowId + "delete", attr("data-query") := queryStr, attr("data-object") := stringify(data.asInstanceOf[js.Dynamic]), cls := "btn btn-round btn-info btn-icon btn-sm like", i(cls := "now-ui-icons ui-1_simple-remove")))
                    case "media" => td(cls := "text-center", a(target := "_blank", href := extractMapOrUser("mediaPath") + "/" +  c.typ.asInstanceOf[String], cls := "btn btn-round btn-info btn-icon btn-sm like", i(cls := "now-ui-icons education_paper")))
                    case _ => {
                      val newValue = obj(c.exp.td(0).name).asInstanceOf[String]
                      val str = if (c.formats.format.isDefined && c.formats.format.get.equals("String")) newValue else Try(c.formats.format.get.format(newValue.toDouble)).getOrElse(Try(Moment(newValue).format(c.formats.format.get)).getOrElse(newValue))
                      td(id := rowId + c.exp.td(0).name.asId, attr("data-format") := "", obj(c.exp.td(0).name).asInstanceOf[String])
                    }
                  }
                ).getOrElse(td(""))
              }).getOrElse(td()))).render)
              loadDropdowns(dropdownsLoad.filterKeys(editQuery.startsWith(_)).values)
              click("savecomplete")
            }
          },
          error = { (jqXHR: JQueryXHR, textStatus: js.Any, errorThrow: js.Any) => click("saveerror") },
          `type` = "POST"
        ).asInstanceOf[JQueryAjaxSettings])
      }
      case "closemulti" => click(lastDialog)
      case "savemultiadd" => {
        val arrayTarget = currentCols.filter(_.typ.equals((if (lastDialog.startsWith("edit")) "edit" else "add"))).head.exp.td.filter(_.multi.isDefined).head
        println("Array name is " + arrayTarget.name)
        val mo = arrayTarget.multi.get.td.foldLeft(Map[String, js.Dynamic]())((m, v) => processCol(m, v))
        click(lastDialog)
        if (mo.keys.size > 0) {
          import js.JSConverters._
          val o = mo.toJSDictionary.asInstanceOf[js.Dynamic]
          println(stringify(o))
          val edtmap = editObj.asInstanceOf[js.Dictionary[js.Dynamic]].toMap
          val eom = edtmap ++ Map(arrayTarget.name -> Try(edtmap(arrayTarget.name).asInstanceOf[js.Array[js.Dynamic]].toSeq ++ Seq(o)).getOrElse(Seq(o)).toJSArray)
          editObj = eom.toJSDictionary.asInstanceOf[js.Dynamic]
          println(stringify(editObj))
          val mainRef = UUID.uuid.replaceAll("-","")
          jQuery("#getmultitable").append(
            tr(
              id := "multientry" + mainRef,
              for (p <- mo.toList zip arrayTarget.multi.get.td) yield if (p._1._2.asInstanceOf[String].equals("true") || p._1._2.asInstanceOf[String].equals("false")) td(
                div(cls := "form-check",
                  label(cls := "form-check-label",
                  input(cls := "form-check-input", `type` := "checkbox", if (p._1._2.asInstanceOf[Boolean]) checked := ""),
                  span(cls := "form-check-sign")
                  )
                )
              ) else if (p._2.formats.dropdownId.isDefined) {
                val mm = Try(dropdowns(p._2.formats.dropdown.get.url)).getOrElse(Map[Int, String]())
                td(cls := "text-left", Try(mm(p._1._2.asInstanceOf[Int])).getOrElse("").asInstanceOf[String])
              } else td(cls := "text-left", p._1._2.asInstanceOf[String]),
              td(cls := "td-actions text-right",
                a(href := "#" + arrayTarget.name + "multiedit" + mainRef, rel := "tooltip", attr("data-object") := stringify(o), cls := "btn btn-info btn-round btn-icon btn-icon-mini btn-neutral", attr("data-original-title") := "Edit Task",
                  i(cls := "now-ui-icons ui-2_settings-90")
                ),
                a(href := "#" + arrayTarget.name + "multidelete" + mainRef, rel := "tooltip", cls := "btn btn-danger btn-round btn-icon btn-icon-mini btn-neutral", attr("data-original-title") := "Remove",
                  i(cls := "now-ui-icons ui-1_simple-remove")
                )
              )
            ).render
          )
        }
      }
      case "saveedit" => {
        val mo = currentCols.filter(_.typ.equals("edit")).head.exp.td.foldLeft(editObj.asInstanceOf[js.Dictionary[js.Dynamic]].toMap)((m, v) => processCol(m, v))
        println(mo)
        import js.JSConverters._
        val o = mo.toJSDictionary.asInstanceOf[js.Dynamic]
        println(stringify(o))
        jQuery.ajax(js.Dynamic.literal(
          url = editQuery,
          data = stringify(o),
          contentType = "application/json",
          success = { (data: js.Any, textStatus: js.Any, jqXHR: JQueryXHR) => {
              val obj = data.asInstanceOf[js.Dynamic]
              jQuery("#" + editRow).attr("data-object", stringify(obj))
              currentCols.filter(c => !c.typ.equals("add") && !c.typ.equals("edit")).foreach(c => Try({
                println("Column " + c.exp.td(0).name + " format " + Try(c.formats.format.get).getOrElse("String"))
                if (c.formats.format.isDefined && c.formats.format.get.equals("Boolean")) {
                  val newValue = obj.extractV(c.exp.td(0).name).asInstanceOf[Boolean]
                  if (newValue) jQuery("#" + editRow + c.exp.td(0).name.asId + "check").attr("checked", "") else jQuery("#" + editRow + c.exp.td(0).name.asId + "check").removeAttr("checked")
                } else if (c.formats.dropdownId.isDefined) Try({
                  val mm = dropdowns(c.formats.dropdown.get.url)
                  val idd = obj.extractV(c.exp.td(0).name).asInstanceOf[Int]
                  jQuery("#" + editRow + c.exp.td(0).name.asId).text(mm(idd))
                }) else {
                  val newValue = obj.extractV(c.exp.td(0).name).asInstanceOf[String]
                  val str = if (c.formats.format.isDefined && c.formats.format.get.equals("String")) newValue else Try(c.formats.format.get.format(newValue.toDouble)).getOrElse(Try(Moment(newValue).format(c.formats.format.get)).getOrElse(newValue))
                  jQuery("#" + editRow + c.exp.td(0).name.asId).text(str)
                }
              }))
              loadDropdowns(dropdownsLoad.filterKeys(editQuery.startsWith(_)).values)
              click("savecomplete")
            }
          },
          error = { (jqXHR: JQueryXHR, textStatus: js.Any, errorThrow: js.Any) => click("saveerror") },
          `type` = "PUT"
        ).asInstanceOf[JQueryAjaxSettings])
      }
      case "savemultiedit" => {
        val mo = currentCols.filter(_.typ.equals("edit")).head.exp.td.filter(_.multi.isDefined).head.multi.get.td.foldLeft(editObjMulti.asInstanceOf[js.Dictionary[js.Dynamic]].toMap)((m,v) => processCol(m, v))
        import js.JSConverters._
        val o = mo.toJSDictionary.asInstanceOf[js.Dynamic]
        println(multiRef + ", " + stringify(o))
        jQuery("#multientry" + multiRef).empty()
        currentCols.filter(_.typ.equals("edit")).head.exp.td.filter(_.multi.isDefined).head.multi.get.td.foreach(pp => jQuery("#multientry" + multiRef).append(if (o.extractV(pp.name).asInstanceOf[String].equals("true") || o.extractV(pp.name).asInstanceOf[String].equals("false")) td(
          div(cls := "form-check",
            label(cls := "form-check-label",
            input(cls := "form-check-input", `type` := "checkbox", if (o.extractV(pp.name).asInstanceOf[Boolean]) checked := ""),
            span(cls := "form-check-sign")
            )
          )
        ).render else if (pp.formats.dropdownId.isDefined) {
          val mm = Try(dropdowns(pp.formats.dropdown.get.url)).getOrElse(Map[Int, String]())
          td(cls := "text-left", Try(mm(o.extractV(pp.name).asInstanceOf[Int])).getOrElse("").asInstanceOf[String]).render
        } else td(cls := "text-left", o.extractV(pp.name).asInstanceOf[String]).render))
        jQuery("#multientry" + multiRef).append(
          td(cls := "td-actions text-right",
            a(href := "#" + arrayNameMulti + "multiedit" + multiRef, rel := "tooltip", attr("data-object") := stringify(o), cls := "btn btn-info btn-round btn-icon btn-icon-mini btn-neutral", attr("data-original-title") := "Edit Task",
              i(cls := "now-ui-icons ui-2_settings-90")
            ),
            a(href := "#" + arrayNameMulti + "multidelete" + multiRef, rel := "tooltip", cls := "btn btn-danger btn-round btn-icon btn-icon-mini btn-neutral", attr("data-original-title") := "Remove",
              i(cls := "now-ui-icons ui-1_simple-remove")
            )
          ).render
        )
        val edtmap = editObj.asInstanceOf[js.Dictionary[js.Dynamic]].toMap
        val array = edtmap(arrayNameMulti).asInstanceOf[js.Array[js.Dynamic]].toSeq
        val newArray = array.zipWithIndex.map(e => if (e._2 == idxMulti) o else e)
        val eom = edtmap ++ Map(arrayNameMulti -> newArray.toJSArray)
        editObj = eom.toJSDictionary.asInstanceOf[js.Dynamic]
        println(stringify(editObj))
        click(lastDialog)
      }
      case "setdate" => {
        val fr = Moment(jQuery("#datefrom").value.asInstanceOf[String])
        val to = Moment(jQuery("#dateto").value.asInstanceOf[String])
        fromSetDate = fr.format().dropRight(6)
        toSetDate = to.format().dropRight(6)
        println("Set range: " + fromSetDate + " to " + toSetDate)
        jQuery("#dateswindow .close").click()
        if (widgetRef.isEmpty) replacePanel(currPanel, true) else {
          val dd = 1 + (to.unix() - fr.unix()) / 86400L
          resetWidget(widgetRef, "Custom", dd.asInstanceOf[Int], false)
          widgetRef = ""
        }
      }
      case _ => if (hr.startsWith("tab_")) repPage(hr.substring(4)) else if (hr.startsWith("setrange")) {
          val days = Try(hr.replaceAll("setrange","").toLong).getOrElse(7L)
          val nowStr = Moment().local().format("MM/DD/YYYY") + " 11:59 PM"
          val fromStr = Moment().local().add(-days + 1, "days").format("MM/DD/YYYY") +  " 00:00 AM"
          println("Setting ranges")
          jQuery("#dateto").value(nowStr)
          jQuery("#datefrom").value(fromStr)
        } else if (hr.contains("multiedit")) {
          val ref = hr.indexOf("multiedit")
          multiRef = hr.slice(ref, hr.length).replaceAll("multiedit", "")
          arrayNameMulti = hr.slice(0, ref)
          val root = e.target.asInstanceOf[org.scalajs.dom.raw.Element].parentNode.asInstanceOf[org.scalajs.dom.raw.Element]
          editObjMulti = JSON.parse(root.attributes.getNamedItem("data-object").value)
          println("Editing " + stringify(editObjMulti))
          jQuery("#getmultitable").children().each((idx: Int, e: org.scalajs.dom.Element) => if ((arrayNameMulti + "multiedit" + e.getAttribute("id").replaceAll("multientry", "")).equals(hr)) idxMulti = idx)
          println("Editing " + idxMulti + " inside " + arrayNameMulti)
          lastDialog = "editobjectwindow"
          click("editobjectwindow")
          jQuery("#multibodyedit").empty()
          jQuery("#multibodyedit").append(allfields(currentCols.filter(_.typ.equals("edit")).head.exp.td.filter(_.multi.isDefined).head.multi.get.td, Some(editObjMulti)).render)
          jQuery("#multibodyedit").append(div(cls := "category form-category","* Required fields").render)
          click("multiwindowedit")
        } else if (hr.contains("multidelete")) {
          val ref = hr.indexOf("multidelete")
          val arrayName = hr.slice(0, ref)
          val hhr = hr.replaceAll(arrayName, "").replaceAll("multidelete","multientry")
          jQuery("#getmultitable").children().each((idx: Int, e: org.scalajs.dom.Element) => if (e.getAttribute("id").equals(hhr)) {
            val edtmap = editObj.asInstanceOf[js.Dictionary[js.Dynamic]].toMap
            val array = edtmap(arrayName).asInstanceOf[js.Array[js.Dynamic]].toSeq
            val newArray = array.zipWithIndex.filter(_._2 != idx).map(_._1)
            val eom = edtmap ++ Map(arrayName -> newArray.toJSArray)
            editObj = eom.toJSDictionary.asInstanceOf[js.Dynamic]
            println(stringify(editObj))
            jQuery("#" + hhr).remove()
          })
        } else if (hr.startsWith("selection")) {
          val values = hr.split("_")
          selections += values(1) -> values(2)
          selections += "mediaPath" -> data.split("_").last
          println("media: " + extractMapOrUser("mediaPath"))
          val id = menus._1.split(",").toList(0)
          updaters.clear
          println("Transition " + menus._2)
          loadDropdowns(dropdownsLoad.values)
          replacePanel(menus._2, true)
          data.split("_").grouped(2).foreach(pair => Try(jQuery("#menuinfo" + pair(0)).text(pair(1))))
        } else if (!targetInd.equals("_blank")) {
          if (mobileSite) Try(document.getElementById("menumobile").asInstanceOf[js.Dynamic].close())
          val l = transitions.filter(t => t.element.equals(hr))
          if (l.length > 0) transit(l(0)) else if (permUpdaters.contains(hr)) permUpdaters(hr)() else {
			  if (dataStore.isEmpty) repPage(hr) else {
				  println("We have a static element: " + hr)
				  e.`type` match {
					  case "blur" => if (dataStore.endsWith("/type=text")) {
						  val nstr = jQuery("#" + hr).value.asInstanceOf[String]
                          val hier = dataStore.split("/").dropRight(1).filter(!_.isEmpty)
						  //jQuery("#" + hr).attr("value", nstr)
						  //println("nstr: " + nstr)
                          updateObject("data", hier, nstr)
					  }
					  case "focus" => if (dataStore.endsWith("/type=text")) {
						  val cstr = jQuery("#" + hr).value
                          val obj = getObject("data")
                          val hier = dataStore.split("/").dropRight(1).filter(!_.isEmpty)
                          val vv = obj.extract(hier)
						  if (vv != null && vv.asInstanceOf[String].equals(cstr)) {
							  println("Setting value of " + hr + " to " + vv.asInstanceOf[String])
							  jQuery("#" + hr).value(vv.asInstanceOf[String])
						  }
					  }
                      case "dblclick" => if (dataStore.endsWith("/type=signature")) {
                        val nstr = jQuery("#" + hr).get(0).asInstanceOf[html.Canvas]
                        sigs += hr -> new SignaturePad(nstr)
                        sigs(hr).clear()
                      }
					  case "click" => if (dataStore.endsWith("/type=checkbox")) {
						  val checked = jQuery("#" + hr).prop("checked").asInstanceOf[Boolean]
                          val hier = dataStore.split("/").dropRight(1).filter(!_.isEmpty)
						  println("checked to store: " + checked)
						  updateObject("data", hier, checked)
					  }
                      case "mouseover" => if (dataStore.endsWith("/type=signature")) jQuery("#menumobile").removeAttr("swipeable")
                      case "mouseleave" => if (dataStore.endsWith("/type=signature")) {
                        jQuery("#menumobile").attr("swipeable","")
                        println("Gathering signature data for " + hr)
                        val nstr = jQuery("#" + hr).get(0).asInstanceOf[html.Canvas]
                        val dta = nstr.toDataURL("image/png")
                        val hier = dataStore.split("/").dropRight(1).filter(!_.isEmpty)
                        //jQuery("#" + hr).attr("value", nstr)
                        println("dta: " + dta)
                        updateObject("data", hier, dta)
                      }
                      case "touchstart" => if (dataStore.endsWith("/type=signature")) jQuery("#menumobile").removeAttr("swipeable")
                      case "touchend" => if (dataStore.endsWith("/type=signature")) {
                        jQuery("#menumobile").attr("swipeable","")
                        println("Gathering signature data for " + hr)
                        val nstr = jQuery("#" + hr).get(0).asInstanceOf[html.Canvas]
                        val dta = nstr.toDataURL("image/png")
                        val hier = dataStore.split("/").dropRight(1).filter(!_.isEmpty)
                        //jQuery("#" + hr).attr("value", nstr)
                        println("dta: " + dta)
                        updateObject("data", hier, dta)
                      }
					  case _ =>
				  }
			  }
		  }
        }
    }
	setTimeout(500) {
		resetEvents
	}
  }

  /*@js.native
  @JSImport("@capacitor/status-bar", "StatusBar")
  object StatusBar extends js.Object {
      def show(): Unit = js.native
      def setOverlaysWebView(overlay:js.Dynamic): Unit = js.native
      def setStyle(style:js.Dynamic): Unit = js.native
  }*/

  def logic {
    if (!mobileSite && "mainpanel".notPresent) {
      //jQuery("select").selectpicker()
      jQuery(document).keypress((e: dom.Event) => {
        val keycode = e.asInstanceOf[js.Dynamic].keyCode.asInstanceOf[Int]
        if (keycode == 13 && "mainpanel".notPresent) Try(transit(transitions(0)))
      })
    }
	setTimeout(500) {
        /*if (mobileSite) {
            val res = js.dynamicImport {
                StatusBar.show()
                StatusBar.setOverlaysWebView(js.Dynamic.literal(overlay = true))
                StatusBar.setStyle(js.Dynamic.literal(style = true))
            }
        }*/
	    resetEvents
	}
  }
}