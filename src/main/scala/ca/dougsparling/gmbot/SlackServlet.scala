package ca.dougsparling.gmbot

import ca.dougsparling.gmbot.parser.{RollSpec, RollSpecParser}
import ca.dougsparling.gmbot.roller.{RollRenderer, RollSpecApproximator, RollSpecRunner, Result, SecureDice, SecureGaussian}
import ca.dougsparling.gmbot.rulebook.{RulebookFinder, RuleOracle, Found, Ambiguous, NotFound}
import org.slf4j.{Logger, LoggerFactory}
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json._
import org.scalatra.Ok

import java.nio.file.{Path, Paths}
import scala.concurrent.{ExecutionContext, Future}

class SlackServlet extends GmBotStack with JacksonJsonSupport {

  protected lazy val logger =  LoggerFactory.getLogger(getClass)
  protected implicit lazy val jsonFormats: Formats = DefaultFormats
  protected lazy val secureRoller = new RollSpecRunner with SecureDice
  protected lazy val secureApproximator = new RollSpecApproximator with SecureGaussian

  protected lazy val rulebooksRoot: Path =
    Paths.get(Option(System.getenv("RULEBOOKS_PATH")).getOrElse("./rulebooks"))

  protected lazy val oracles: Map[Path, RuleOracle] =
    RulebookFinder.listAll(rulebooksRoot)
      .flatMap(name => RulebookFinder.resolve(name, rulebooksRoot) match
        case Found(_, path) => Some(path -> new RuleOracle(path))
        case _              => None)
      .toMap

  protected given ec: ExecutionContext = ExecutionContext.global

  before() {
    contentType = formats("json")
  }

  get("/health") {
    Ok("hello")
  }

  post("/roll") {
    val req = parseRequest()
    val parseResult = RollSpecParser.parseAll(RollSpecParser.roll, req.text)
    logger.info(s"Received $req")
    parseResult match {
      case RollSpecParser.NoSuccess(msg, _) => helpMessage(msg, req.command)
      case RollSpecParser.Success(spec, _) =>
        RollRenderer.validateSpec(spec).map(err => Ok(slackResponse(err))).getOrElse {
          if spec.shouldApproximate then
            Ok(renderApproximate(req, spec, secureApproximator.approximate(spec)))
          else
            secureRoller.run(spec) match {
              case Left(rolls) => Ok(renderResult(req, spec, rolls))
              case Right(err)  => Ok(slackResponse(err))
            }
        }
    }
  }

  post("/rule") {
    val req   = parseRequest()
    val parts = req.text.trim.split("\\s+", 2)
    if parts.length < 2 then
      val all = RulebookFinder.listAll(rulebooksRoot)
      val list = if all.isEmpty then "_No rulebooks found._"
                 else all.map(n => s"• `$n`").mkString("\n")
      Ok(slackResponse(s"Available rulebooks:\n$list\nUsage: `/rule <ruleset> <question>`"))
    else
      val (prefix, question) = (parts(0), parts(1))
      handleRuleQuery(req, prefix, question)
  }

  post("/gm") {
    val req      = parseRequest()
    val trailer = req.text.trim
    if trailer.isEmpty then
      Ok(slackResponse("Usage: `/gm <statement or question>`"))
    else
      handleRuleQuery(req, "gm", trailer)
  }

  private def handleRuleQuery(req: SlackRequest, prefix: String, question: String) = {
    RulebookFinder.resolve(prefix, rulebooksRoot) match
      case NotFound           => Ok(slackResponse(s"No rulebook found matching `$prefix`."))
      case Ambiguous(matches) => Ok(slackResponse(s"Ambiguous: `$prefix` matches ${matches.mkString(", ")}"))
      case Found(name, path)  =>
        if req.responseUrl.isEmpty then Ok(slackResponse("Error: no response_url from Slack."))
        else
          val mention = if req.userId.nonEmpty then s"<@${req.userId}>" else s"@${req.who}"
          val preface = s"$mention consults *$name*: _${question}_"
          val oracle = oracles.getOrElse(path, new RuleOracle(path))
          Future { oracle.ask(question, req.responseUrl, preface) }
          Ok(ephemeralResponse(s"Consulting *$name*\u2026 hang tight"))
  }

  def parseRequest() = {
    SlackRequest(params("text"), params("command"), params("user_name"), params.getOrElse("response_url", ""), params.getOrElse("user_id", ""))
  }

  def renderResult(request: SlackRequest, spec: RollSpec, rolls: Result) =
    slackResponse(
      RollRenderer.formatSummary(rolls, spec, request.who),
      RollRenderer.formatBatches(rolls, spec)
    )

  def renderApproximate(request: SlackRequest, spec: RollSpec, result: Result) =
    slackResponse(RollRenderer.formatApproximate(result, spec, request.who))

  def helpMessage(hint: String, command: String) =
    Ok(slackResponse(s"_Help_: `$command` — ${RollRenderer.helpText(hint)}"))

  def slackResponse(text: String, attachments: String*) = {
    contentType = formats("json")
    SlackResponse(text, attachments.map(Attachment.apply))
  }

  def ephemeralResponse(text: String) = {
    contentType = formats("json")
    SlackResponse(text, Seq.empty, response_type = "ephemeral")
  }

}
case class SlackRequest(text: String, command: String, who: String, responseUrl: String = "", userId: String = "")
case class Attachment(text: String)
case class SlackResponse(text: String, attachments: Seq[Attachment], response_type: String = "in_channel")
