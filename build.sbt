val ScalatraVersion = "3.1.2"

organization := "ca.dougsparling"

name := "gm-bot"

version := "1.0.0"

scalaVersion := "3.8.2"

libraryDependencies ++= Seq(
  "org.scalatra" %% "scalatra-jakarta" % ScalatraVersion,
  "org.scalatra" %% "scalatra-scalatest-jakarta" % ScalatraVersion % "test",
  "org.scalatra" %% "scalatra-specs2-jakarta" % ScalatraVersion % "test",
  "org.scalatra" %% "scalatra-json-jakarta" % ScalatraVersion,
  "org.json4s"   %% "json4s-jackson" % "4.0.7",
  "ch.qos.logback" % "logback-classic" % "1.5.32" % "runtime",
  "org.eclipse.jetty.ee10" % "jetty-ee10-webapp" % "12.1.7" % "container;compile",
  "jakarta.servlet" % "jakarta.servlet-api" % "6.1.0" % "provided"
)

Compile / run / mainClass := Some("ca.dougsparling.JettyLauncher")

assembly / assemblyMergeStrategy := {
  case "module-info.class"                                          => MergeStrategy.discard
  case PathList("META-INF", "versions", _, "module-info.class")    => MergeStrategy.discard
  case x => (assembly / assemblyMergeStrategy).value(x)
}

enablePlugins(SbtTwirl)
enablePlugins(ScalatraPlugin)
