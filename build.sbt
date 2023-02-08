enablePlugins(ScalaJSPlugin)
enablePlugins(JSDependenciesPlugin)

name := "iW8"

organization := "Berrye Inc."

scalaVersion := "2.13.10"

jsEnv := PhantomJSEnv().value

libraryDependencies ++= Seq(
	"org.scala-lang" % "scala-reflect" % "2.13.10",
	"org.scala-js" %%% "scalajs-dom" % "1.1.0",
	"be.doeraene" %%% "scalajs-jquery" % "1.0.0",
	"com.lihaoyi" %%% "scalatags" % "0.9.2",
	"ru.pavkin" %%% "scala-js-momentjs" % "0.10.5",
	"org.scala-lang.modules" %% "scala-async" % "1.0.0-M1",
	"com.github.benhutchison" %%% "prickle" % "1.1.16",
	"org.scala-lang.modules" %%% "scala-parser-combinators" % "1.1.2")

jsDependencies += "org.webjars.bower" % "jshashes" % "1.0.7" / "1.0.7/hashes.min.js"

scalaJSUseMainModuleInitializer := true