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
  "jakarta.servlet" % "jakarta.servlet-api" % "6.1.0" % "provided",
  "com.slack.api" % "slack-app-backend" % "1.45.3",
  "dev.langchain4j" % "langchain4j-open-ai" % "1.12.2",
  "dev.langchain4j" % "langchain4j-google-ai-gemini" % "1.0.0-beta5"
)

Compile / run / mainClass := Some("ca.dougsparling.JettyLauncher")

// jcl-over-slf4j provides the same classes as commons-logging as an SLF4J bridge;
// exclude the real commons-logging to avoid duplicate class conflicts at assembly time.
excludeDependencies ++= Seq(
  ExclusionRule("commons-logging", "commons-logging")
)

assembly / assemblyMergeStrategy := {
  case "module-info.class"                                          => MergeStrategy.discard
  case PathList("META-INF", "versions", _, "module-info.class")    => MergeStrategy.discard
  case PathList("META-INF", f) if f.endsWith(".kotlin_module")     => MergeStrategy.discard
  case PathList("META-INF", "services", _*)                        => MergeStrategy.concat
  case PathList("META-INF", "versions", _, "OSGI-INF", _*)        => MergeStrategy.first
  case PathList("META-INF", f) if f.endsWith(".properties")        => MergeStrategy.first
  case PathList("META-INF", f) if f.endsWith(".json")              => MergeStrategy.first
  case PathList("META-INF", "native-image", _*)                   => MergeStrategy.discard
  case x => (assembly / assemblyMergeStrategy).value(x)
}

enablePlugins(SbtTwirl)
enablePlugins(ScalatraPlugin)
