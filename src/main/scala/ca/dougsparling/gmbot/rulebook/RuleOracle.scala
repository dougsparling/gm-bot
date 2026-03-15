package ca.dougsparling.gmbot.rulebook

import com.google.adk.agents.LlmAgent
import com.google.adk.events.Event
import com.google.adk.runner.InMemoryRunner
import com.google.adk.tools.FunctionTool
import com.google.genai.types.{Content, Part}
import org.slf4j.LoggerFactory

import java.io.IOException
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.{Files, Path}
import java.util.stream.Collectors
import scala.jdk.CollectionConverters.*

object RuleOracle:
  private val MODEL = "gemini-3.1-flash-lite-preview"
  private val SYSTEM_PROMPT_BASE =
    "You are a tabletop RPG rules assistant.\n\n" +
    "Available tools:\n" +
    "- readFile(filename): reads the full content of a file from the rulebook directory\n" +
    "- listFiles(subdirectory): lists files in the rulebook directory or a subdirectory; pass an empty string for the root\n" +
    "- searchFiles(query): searches all rulebook files for lines containing the query string; returns up to 20 matches as filename:line: content\n\n" +
    "A rulebook index is provided below. Use the index to identify the most relevant file and call readFile directly. " +
    "Only use searchFiles if the index does not clearly identify a target file. " +
    "Do not call listFiles unless neither the index nor searchFiles gives enough direction. " +
    "Read additional files only if the first file does not contain enough to answer the question. " +
    "If the rulebook does not cover the question, say so clearly. Do not invent rules. " +
    "Always include a fenced code block (``` ... ```) with the exact passage from the file that supports your answer. " +
    "Format your response using standard markdown.\n\n"

class RuleOracle(rulebookPath: Path):
  import RuleOracle.*

  private val logger = LoggerFactory.getLogger(getClass)
  private val root   = rulebookPath.toAbsolutePath.normalize()

  private def loadIndex(): String =
    try Files.readString(root.resolve("index.md"))
    catch case e: IOException =>
      logger.warn("Could not load index.md: {}", e.getMessage)
      "(index unavailable)"

  def readFile(filename: String): String =
    try
      val resolved = root.resolve(filename).normalize()
      if !resolved.startsWith(root) then
        "Error: access denied — path is outside the rulebook directory."
      else if !Files.isRegularFile(resolved) then
        s"Error: file not found: $filename"
      else
        Files.readString(resolved)
    catch case e: IOException =>
      logger.warn("readFile failed for {}: {}", filename, e.getMessage)
      s"Error reading file: ${e.getMessage}"

  def searchFiles(query: String): String =
    try
      val lq = query.toLowerCase
      val hits = new java.util.ArrayList[String]()
      Files.walk(root)
        .filter(p => p.toString.endsWith(".md") && Files.isRegularFile(p))
        .forEach { path =>
          val rel = root.relativize(path).toString
          Files.readAllLines(path).asScala.zipWithIndex.foreach { (line, i) =>
            if line.toLowerCase.contains(lq) then
              hits.add(s"$rel:${i + 1}: $line")
          }
        }
      if hits.isEmpty then s"No matches found for: $query"
      else hits.asScala.take(20).mkString("\n")
    catch case e: IOException =>
      logger.warn("searchFiles failed for {}: {}", query, e.getMessage)
      s"Error searching files: ${e.getMessage}"

  def listFiles(subdirectory: String): String =
    try
      val target =
        if subdirectory.isEmpty then root
        else root.resolve(subdirectory).normalize()
      if !target.startsWith(root) then
        "Error: access denied — path is outside the rulebook directory."
      else if !Files.isDirectory(target) then
        s"Error: not a directory: $subdirectory"
      else
        Files.list(target)
          .map(p => p.getFileName.toString + (if Files.isDirectory(p) then "/" else ""))
          .sorted()
          .collect(Collectors.joining("\n"))
    catch case e: IOException =>
      logger.warn("listFiles failed for {}: {}", subdirectory, e.getMessage)
      s"Error listing files: ${e.getMessage}"

  def ask(question: String, responseUrl: String, preface: String): Unit =
    try
      val agent = LlmAgent.builder()
        .model(MODEL)
        .name("rulebook_assistant")
        .description("Answers tabletop RPG rules questions from local rulebook files")
        .instruction(SYSTEM_PROMPT_BASE + loadIndex())
        .tools(
          FunctionTool.create(this, "listFiles"),
          FunctionTool.create(this, "readFile"),
          FunctionTool.create(this, "searchFiles")
        )
        .build()

      val runner  = new InMemoryRunner(agent)
      val session = runner.sessionService().createSession(agent.name(), "slack-user").blockingGet()
      val events  = runner.runAsync("slack-user", session.id(), Content.fromParts(Part.fromText(question)))
        .toList.blockingGet()

      val answer = events.stream()
        .filter(_.finalResponse())
        .map(_.stringifyContent())
        .findFirst()
        .orElse("I could not find an answer in the rulebook.")

      postToSlack(responseUrl, s"$preface\n$answer", buildTrace(events))

    catch case e: Exception =>
      logger.error("RuleOracle.ask failed", e)
      postToSlack(responseUrl, s"$preface\nSorry, I encountered an error looking up the rules: ${e.getMessage}", "")

  private def buildTrace(events: java.util.List[Event]): String =
    val sb = new StringBuilder
    for e <- events.asScala if !e.finalResponse() && e.content().isPresent do
      for p <- e.content().get().parts().get().asScala do
        p.functionCall().ifPresent { fc =>
          val name = fc.name().orElse("?")
          val args = fc.args()
            .map(m => m.values().stream().map[String](_.toString).collect(Collectors.joining(", ")))
            .orElse("")
          sb.append(s"`$name(\"$args\")`\n")
        }
        p.functionResponse().ifPresent { fr =>
          val content = fr.response()
            .map(m => String.valueOf(m.asInstanceOf[java.util.Map[String, Object]].getOrDefault("result", m)))
            .orElse("")
          val headings = content.linesIterator.filter(l => l.startsWith("# ") || l.startsWith("## ")).mkString(", ")
          val summary  = if headings.nonEmpty then headings else "(no headings)"
          sb.append(s"→ $summary\n")
        }
    sb.toString.trim

  private def postToSlack(responseUrl: String, text: String, trace: String): Unit =
    val blocks = new StringBuilder
    blocks.append(s"""[{"type":"markdown","text":${jsonString(text)}}""")
    if trace.nonEmpty then
      blocks.append(s""",{"type":"section","expand":false,"text":{"type":"mrkdwn","text":${jsonString(s"*Tool trace*\n$trace")}}}""")
    blocks.append("]")
    val json = s"""{"response_type":"in_channel","blocks":$blocks}"""
    try
      val client   = HttpClient.newHttpClient()
      val request  = HttpRequest.newBuilder()
        .uri(URI.create(responseUrl))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build()
      val response = client.send(request, HttpResponse.BodyHandlers.ofString())
      if response.statusCode() == 200 then
        logger.info("Slack response_url POST status: 200")
      else
        logger.warn("Slack response_url POST status: {} body: {}", response.statusCode(), response.body())
    catch case e: Exception =>
      logger.error("Failed to POST to Slack response_url", e)

  private def jsonString(s: String): String =
    val sb = new StringBuilder("\"")
    for c <- s do c match
      case '"'  => sb.append("\\\"")
      case '\\' => sb.append("\\\\")
      case '\n' => sb.append("\\n")
      case '\r' => sb.append("\\r")
      case '\t' => sb.append("\\t")
      case c if c < 0x20 => sb.append(f"\\u${c.toInt}%04x")
      case c => sb.append(c)
    sb.append("\"").toString
