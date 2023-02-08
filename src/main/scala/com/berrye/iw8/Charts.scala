package com.berrye.iw8

import scala.util.{ Try, Success, Failure }
import scala.collection._
import scala.scalajs.js
import scalatags.JsDom.all._
import scalatags.JsDom._
import js.Dynamic.{ global => g }
import org.scalajs.dom._
import js.annotation._
import js.annotation.JSExport
import js.JSConverters._
import org.scalajs.dom._

package object Charts {

  @js.native
  trait ChartDataset extends js.Object {
    def label: String = js.native
    def data: js.Array[Double] = js.native
    def fillColor: String = js.native
    def strokeColor: String = js.native
  }

  object ChartDataset {
    def apply(
      data: Seq[Double],
      labels: Seq[String],
      backgroundColor: Seq[String] = Seq("#00A1DE", "#D3D3D3"),
      hoverBackgroundColor: Seq[String] = Seq("#00A1DE", "#D3D3D3"),      
      borderColor: Seq[String] = Seq("#404080","#404080"),
      borderWidth: Seq[Int] = Seq(1,1)): ChartDataset = {
      js.Dynamic.literal(
        labels = labels.toJSArray,
        data = data.toJSArray,
        backgroundColor = backgroundColor.toJSArray,
        hoverBackgroundColor.toJSArray,
        borderColor = borderColor.toJSArray,
        borderWidth = borderWidth.toJSArray).asInstanceOf[ChartDataset]
    }
  }

  @js.native
  trait ChartData extends js.Object {
    def labels: js.Array[String] = js.native
    def datasets: js.Array[ChartDataset] = js.native
  }

  object ChartData {
    def apply(labels: Seq[String], datasets: Seq[ChartDataset]): ChartData = {
      js.Dynamic.literal(
        labels = labels.toJSArray,
        datasets = datasets.toJSArray).asInstanceOf[ChartData]
    }
  }

  @js.native
  trait ChartOptions extends js.Object {
    def responsive: Boolean = js.native
  }

  object ChartOptions {
    def apply(responsive: Boolean = true): ChartOptions = {
      js.Dynamic.literal(
        responsive = responsive).asInstanceOf[ChartOptions]
    }
  }

  @js.native
  trait ChartConfiguration extends js.Object {
    def `type`: String = js.native
    def data: ChartData = js.native
    def options: js.Object = js.native
  }

  object ChartConfiguration {
    def apply(`type`: String, data: ChartData, options: js.Object = ChartOptions(false)): ChartConfiguration = {
      js.Dynamic.literal(
        `type` = `type`,
        data = data,
        options = options).asInstanceOf[ChartConfiguration]
    }
  }

   // define a class to access the Chart.js component
  @js.native
  @JSGlobal("Chart")
  object JSChart extends js.Object {
    def pluginservice: js.Dynamic = js.native
  }

  // define a class to access the Chart.js component
  @js.native
  @JSGlobal("Chart")
  class JSChart(ctx: CanvasRenderingContext2D, config: js.Object) extends js.Object {
    def update(): Unit = js.native
  }
}
