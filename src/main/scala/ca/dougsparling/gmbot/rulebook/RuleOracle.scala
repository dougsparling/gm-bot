package ca.dougsparling.gmbot.rulebook

import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.{AiMessage, ChatMessage, SystemMessage, ToolExecutionResultMessage, UserMessage}
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import ca.dougsparling.gmbot.parser.RollSpecParser
import ca.dougsparling.gmbot.roller.{Dropped, Kept, Rerolled, RollSpecRunner, SecureDice}
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel
import dev.langchain4j.model.openai.{OpenAiChatModel, OpenAiChatRequestParameters}
import org.slf4j.LoggerFactory

import java.io.IOException
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.{Files, Path}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*

object RuleOracle:
  private val baseUrl   = sys.env("LLM_BASE_URL").trim.stripSuffix("/")
  private val modelName = sys.env("LLM_MODEL").trim
  private val apiKey    = sys.env("LLM_API_KEY").trim
  private val isLocal   = baseUrl.contains("://localhost") || baseUrl.contains("://192.168.")
  private val isGemini  = modelName.startsWith("gemini")

  private val model: ChatModel =
    if isGemini then
      GoogleAiGeminiChatModel.builder()
        .apiKey(apiKey)
        .modelName(modelName)
        .build()
    else
      val builder = OpenAiChatModel.builder()
        .baseUrl(baseUrl)
        .apiKey(apiKey)
        .modelName(modelName)
      if isLocal then builder.defaultRequestParameters(OpenAiChatRequestParameters.builder()
        .customParameters(java.util.Map.of("cache_prompt", true))
        .build())
      builder.build()

  private val roller = new RollSpecRunner with SecureDice

  private val tools: java.util.List[ToolSpecification] = java.util.List.of(
    ToolSpecification.builder()
      .name("readFile")
      .description("reads the full content of a file from the rulebook directory")
      .parameters(JsonObjectSchema.builder()
        .addStringProperty("filename", "the relative filename to read")
        .required(java.util.List.of("filename"))
        .build())
      .build(),
    ToolSpecification.builder()
      .name("searchFiles")
      .description("searches all rulebook files for lines containing the query string; returns up to 20 matches as filename:line:content")
      .parameters(JsonObjectSchema.builder()
        .addStringProperty("query", "the search string")
        .required(java.util.List.of("query"))
        .build())
      .build(),
    ToolSpecification.builder()
      .name("rollDice")
      .description("rolls dice using a spec string; full syntax: [Nx times] [N]dS [+/-M] [reroll N|N to M] [drop highest|lowest [N]]; " +
        "examples: '4d6', 'd20+5', '3 times 2d6+3', '4 times 4d6 drop lowest', '2d8 reroll 1 to 2'; " +
        "use 'N times' to roll multiple times in one call instead of calling this tool repeatedly")
      .parameters(JsonObjectSchema.builder()
        .addStringProperty("spec", "the dice roll specification")
        .required(java.util.List.of("spec"))
        .build())
      .build()
  )

  private val SYSTEM_PROMPT_BASE =
    "You are a tabletop RPG rules assistant.\n\n" +
    "Available tools:\n" +
    "- readFile(filename): reads the full content of a file from the rulebook directory\n" +
    "- searchFiles(query): searches all rulebook files for lines containing the query string; returns up to 20 matches as filename:line:content\n\n" +
    "A rulebook index is provided below. Use the index to identify the most relevant file and call readFile directly. " +
    "Only use searchFiles if the index does not clearly identify a target file. " +
    "If searchFiles returns any results, immediately read the file from the first result rather than searching again with variations. " +
    "If the user asks to roll dice, first look up the relevant rule in the rulebook to confirm the dice spec, then call rollDice with the spec from the rulebook. Never roll from memory — always verify the spec first. Never ask for confirmation before rolling; just roll. " +
    "Read additional files only if the first file does not contain enough to answer the question. " +
    "If the rulebook does not cover the question, say so clearly. Do not invent rules. " +
    "When citing a passage from the rulebook, include it inline in your response without any code block or blockquote formatting. " +
    "Format your response using standard markdown.\n\n" +
    "/no_think\n\n"

class RuleOracle(rulebookPath: Path):
  import RuleOracle.*

  private val logger = LoggerFactory.getLogger(getClass)
  private val root   = rulebookPath.toAbsolutePath.normalize()

  private val index: String =
    try Files.readString(root.resolve("index.md"))
    catch case e: IOException =>
      logger.warn("Could not load index.md: {}", e.getMessage)
      "(index unavailable)"

  // Pre-warm: loads the model and seeds the KV cache with the system prompt + index.
  // Runs in the background so it doesn't block startup or the first request.
  Future {
    try
      val req = ChatRequest.builder()
        .messages(java.util.List.of(
          SystemMessage.from(SYSTEM_PROMPT_BASE + index),
          UserMessage.from("ready")))
        .build()
      model.chat(req)
      logger.info("RuleOracle pre-warm complete for {}", root)
    catch case e: Exception =>
      logger.warn("RuleOracle pre-warm failed (continuing): {}", e.getMessage)
  }(ExecutionContext.global)

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
      val lq      = query.toLowerCase
      val results = scala.collection.mutable.ListBuffer[String]()
      val seen    = scala.collection.mutable.Set[(String, Int)]()

      Files.walk(root)
        .filter(p => p.toString.endsWith(".md") && Files.isRegularFile(p))
        .forEach { path =>
          if results.size < 3 then
            val rel   = root.relativize(path).toString
            val lines = Files.readAllLines(path).asScala.toVector
            lines.zipWithIndex.foreach { (line, idx) =>
              if line.toLowerCase.contains(lq) && results.size < 3 then
                val sectionStart = (idx to 0 by -1).find(i => lines(i).startsWith("#")).getOrElse(0)
                if !seen.contains((rel, sectionStart)) then
                  seen += ((rel, sectionStart))
                  val level      = lines(sectionStart).takeWhile(_ == '#').length
                  val sectionEnd = (sectionStart + 1 until lines.length).find { i =>
                    val l = lines(i)
                    l.startsWith("#") && l.takeWhile(_ == '#').length <= level
                  }.getOrElse(lines.length)
                  val text = lines.slice(sectionStart, sectionEnd).mkString("\n").trim
                  results += s"[$rel]\n$text"
            }
        }
      if results.isEmpty then s"No matches found for: $query"
      else results.mkString("\n\n---\n\n")
    catch case e: IOException =>
      logger.warn("searchFiles failed for {}: {}", query, e.getMessage)
      s"Error searching files: ${e.getMessage}"

  def rollDice(spec: String): String =
    RollSpecParser.parseAll(RollSpecParser.roll, spec) match
      case RollSpecParser.Success(rollSpec, _) =>
        roller.run(rollSpec) match
          case Left(result) =>
            result.batches.map { batch =>
              val rolls = batch.rolls.map {
                case r if r.fate == Kept     => s"${r.n}"
                case r if r.fate == Rerolled => s"${r.n}(rerolled)"
                case r if r.fate == Dropped  => s"${r.n}(dropped)"
              }.mkString(", ")
              s"[$rolls] = ${batch.sum(rollSpec.modifier)}${if rollSpec.modifier != 0 then s" (with +${rollSpec.modifier})" else ""}"
            }.mkString("\n")
          case Right(err) => s"Error: $err"
      case _ => s"Could not parse dice spec: '$spec'"

  def ask(question: String, responseUrl: String, preface: String): Unit =
    val startTime = System.currentTimeMillis()
    try
      val messages = new java.util.ArrayList[ChatMessage]()
      messages.add(SystemMessage.from(SYSTEM_PROMPT_BASE + index))
      messages.add(UserMessage.from(question))

      val trace = new StringBuilder
      var answer = "I could not find an answer in the rulebook."
      var done = false

      while !done do
        val request  = ChatRequest.builder().messages(messages).toolSpecifications(tools).build()
        val response = model.chat(request)
        val aiMsg    = response.aiMessage()
        messages.add(aiMsg)

        if aiMsg.hasToolExecutionRequests() then
          for req <- aiMsg.toolExecutionRequests().asScala do
            trace.append(s"`${req.name()}(${req.arguments()})`\n")
            val (result, summary) = dispatchTool(req.name(), req.arguments())
            trace.append(s"→ $summary\n")
            messages.add(ToolExecutionResultMessage.from(req, result))
        else
          answer = Option(aiMsg.text()).getOrElse("I could not find an answer in the rulebook.")
          done = true

      val elapsed = (System.currentTimeMillis() - startTime) / 1000
      postToSlack(responseUrl, s"$preface\n$answer", trace.toString.trim, elapsed)

    catch case e: Exception =>
      logger.error("RuleOracle.ask failed", e)
      val msg = e.getMessage
      val retryMsg =
        if msg != null && msg.contains("429") then
          val retry = "Please retry in ([\\d.]+s)".r.findFirstMatchIn(msg).map(_.group(1))
          retry.fold("Agent quota exceeded, please try again later.")(t => s"Agent quota exceeded, please try again in $t")
        else
          s"Sorry, I encountered an error: $msg"
      postToSlack(responseUrl, s"$preface\n$retryMsg", "")

  private def dispatchTool(name: String, argsJson: String): (String, String) =
    import org.json4s.*
    import org.json4s.jackson.JsonMethods.*
    implicit val formats: Formats = DefaultFormats
    val args = parse(argsJson)
    name match
      case "readFile" =>
        val result   = readFile((args \ "filename").extractOpt[String].getOrElse(""))
        val headings = result.linesIterator.filter(l => l.startsWith("# ") || l.startsWith("## ")).mkString(", ")
        (result, if headings.nonEmpty then headings else "(no headings)")
      case "searchFiles" =>
        val result = searchFiles((args \ "query").extractOpt[String].getOrElse(""))
        val n      = result.split("\n\n---\n\n").count(_.nonEmpty)
        (result, if result.startsWith("No matches") then "0 sections" else s"$n section(s)")
      case "rollDice" =>
        val spec   = (args \ "spec").extractOpt[String].getOrElse("")
        val result = rollDice(spec)
        (result, result)
      case other =>
        (s"Unknown tool: $other", "unknown tool")

  private def toMrkdwn(md: String): String =
    var s = md
    // Remove language hint from fenced code blocks
    s = s.replaceAll("(?m)^```[a-zA-Z0-9]+$", "```")
    // Horizontal rules (---, ***, ___) → blank line
    s = s.replaceAll("(?m)^(\\*{3,}|-{3,}|_{3,})\\s*$", "")
    // Headings → bold
    s = s.replaceAll("(?m)^#{1,6}\\s+(.+)$", "*$1*")
    // Bold+italic — must come before bold
    s = s.replaceAll("\\*\\*\\*(.+?)\\*\\*\\*", "*_$1_*")
    // Bold
    s = s.replaceAll("\\*\\*(.+?)\\*\\*", "*$1*")
    // Strikethrough
    s = s.replaceAll("~~(.+?)~~", "~$1~")
    // Links
    s = s.replaceAll("\\[(.+?)\\]\\((.+?)\\)", "<$2|$1>")
    // Tables — wrap in code block for monospace alignment; strip separator rows
    s = wrapTables(s)
    s

  private def wrapTables(text: String): String =
    val lines = text.split("\n", -1).toList
    val result = scala.collection.mutable.ListBuffer[String]()
    var inTable = false
    for line <- lines do
      val isTableRow  = line.trim.startsWith("|")
      val isSeparator = line.matches("\\s*\\|[-| :]+\\|\\s*")
      if isTableRow && !inTable then
        inTable = true
        result += "```"
        if !isSeparator then result += line
      else if isTableRow then
        if !isSeparator then result += line
      else
        if inTable then
          inTable = false
          result += "```"
        result += line
    if inTable then result += "```"
    result.mkString("\n")

  private def splitIntoBlocks(text: String, maxLen: Int, maxBlocks: Int): List[String] =
    val lines  = text.split("\n", -1).toList
    val chunks = scala.collection.mutable.ListBuffer[String]()
    val current = new StringBuilder
    for line <- lines do
      val addition = if current.isEmpty then line else s"\n$line"
      if current.length + addition.length > maxLen then
        if current.nonEmpty then chunks += current.toString
        current.clear()
        // line itself exceeds maxLen — hard split
        var remaining = line
        while remaining.nonEmpty do
          chunks += remaining.take(maxLen)
          remaining = remaining.drop(maxLen)
      else
        current.append(addition)
    if current.nonEmpty then chunks += current.toString
    chunks.take(maxBlocks).toList

  private def postToSlack(responseUrl: String, text: String, trace: String, elapsed: Long = 0): Unit =
    import org.json4s.*
    import org.json4s.jackson.JsonMethods.*
    implicit val formats: Formats = DefaultFormats

    val maxBlocks   = if trace.nonEmpty then 49 else 50
    val textBlocks  = splitIntoBlocks(toMrkdwn(text), 3000, maxBlocks).map(chunk =>
      JObject("type" -> JString("section"), "text" -> JObject("type" -> JString("mrkdwn"), "text" -> JString(chunk)))
    )
    val traceBlock  = JObject(
      "type"     -> JString("context"),
      "elements" -> JArray(List(JObject(
        "type" -> JString("mrkdwn"),
        "text" -> JString(s"*Tool trace* ($modelName, took ${elapsed}s)\n$trace")
      )))
    )
    val blocks = if trace.nonEmpty then JArray(textBlocks :+ traceBlock)
                 else JArray(textBlocks)
    val payload = compact(render(JObject(
      "response_type" -> JString("in_channel"),
      "blocks"        -> blocks
    )))

    logger.info("Posting to Slack: {}", payload)
    try
      val client   = HttpClient.newHttpClient()
      val request  = HttpRequest.newBuilder()
        .uri(URI.create(responseUrl))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build()
      val response = client.send(request, HttpResponse.BodyHandlers.ofString())
      if response.statusCode() == 200 then
        logger.info("Slack response_url POST status: 200")
      else
        logger.warn("Slack response_url POST status: {} body: {}", response.statusCode(), response.body())
    catch case e: Exception =>
      logger.error("Failed to POST to Slack response_url", e)
