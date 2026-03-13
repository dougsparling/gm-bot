package ca.dougsparling.gmbot

import ca.dougsparling.gmbot.parser.{RollSpec, RollSpecParser}
import ca.dougsparling.gmbot.roller.{RollSpecApproximator, _}
import ca.dougsparling.gmbot.rulebook.{RulebookFinder, RuleOracle, Found, Ambiguous, NotFound}
import org.slf4j.{Logger, LoggerFactory}
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json._
import org.scalatra.Ok

import java.nio.file.{Path, Paths}
import scala.concurrent.{ExecutionContext, Future}

class GmBotServlet extends GmBotStack with JacksonJsonSupport {

  protected lazy val logger =  LoggerFactory.getLogger(getClass)
  protected implicit lazy val jsonFormats: Formats = DefaultFormats
  protected lazy val secureRoller = new RollSpecRunner with SecureDice
  protected lazy val secureApproximator = new RollSpecApproximator with SecureGaussian

  protected lazy val rulebooksRoot: Path =
    Paths.get(Option(System.getenv("RULEBOOKS_PATH")).getOrElse("./rulebooks"))

  protected given ec: ExecutionContext = ExecutionContext.global

  get("/health") {
    Ok("hello")
  }

  post("/roll") {
    val req = parseRequest()
    val parseResult = RollSpecParser.parseAll(RollSpecParser.roll, req.text)
    logger.info(s"Received $req")
    parseResult match {
      case RollSpecParser.NoSuccess(msg, next) => helpMessage(msg, req.command)
      case RollSpecParser.Success(spec, _) if spec.die >= 10000 => Ok(slackResponse(s"Sorry, I can't find a ${spec.die} sided die."))
      case RollSpecParser.Success(spec, _) if spec.reroll.exists(_.size >= spec.die) => Ok(slackResponse("All dice would be rerolled"))
      case RollSpecParser.Success(spec, _) if spec.shouldApproximate => {
        Ok(renderApproximate(req, spec, secureApproximator.approximate(spec)))
      }
      case RollSpecParser.Success(spec, _) => {
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
      RulebookFinder.resolve(prefix, rulebooksRoot) match
        case NotFound           => Ok(slackResponse(s"No rulebook found matching `$prefix`."))
        case Ambiguous(matches) => Ok(slackResponse(s"Ambiguous: `$prefix` matches ${matches.mkString(", ")}"))
        case Found(name, path)  =>
          if req.responseUrl.isEmpty then Ok(slackResponse("Error: no response_url from Slack."))
          else
            val mention = if req.userId.nonEmpty then s"<@${req.userId}>" else s"@${req.who}"
            Future { new RuleOracle(path).ask(question, req.responseUrl, mention) }
            Ok(ephemeralResponse(s"Consulting *$name*\u2026 hang tight"))
  }

  before() {
    contentType = formats("json")
  }

  def parseRequest() = {
    //val reqMap = body.split("\n").map(_.split("=")).map(pair => (pair(0), pair(1))).toMap
    SlackRequest(params("text"), params("command"), params("user_name"), params.getOrElse("response_url", ""), params.getOrElse("user_id", ""))
  }

  def renderResult(request: SlackRequest, spec: RollSpec, rolls: Result) = {
    val totals = rolls.batches.map(_.sum(spec.modifier)).mkString(", ")
    val summary = s"Rolled for ${request.who}: $totals"
    val extended = rolls.batches.map { batch =>
      val tally = batch.rolls.zipWithIndex.map { case (roll, index) =>
        roll match {
          case Roll(n, Kept) => s"$n"
          case Roll(n, Rerolled) => s"$n (rerolled)"
          case Roll(n, Dropped) => s"$n (dropped)"
        }
      }.mkString(", ")

      val modifier = if (spec.modifier == 0) {
        ""
      } else {
        s" + ${spec.modifier}"
      }

      val sum = batch.sum(spec.modifier)

      s"$tally$modifier = $sum"
    }.mkString("\n")

    slackResponse(summary, extended)
  }

  def renderApproximate(request: SlackRequest, spec: RollSpec, result: Result) = {
    val totals = result.batches.map(b => s"≈ ${b.sum(spec.modifier)}").mkString(", ")
    slackResponse(s"Rolled for ${request.who}: $totals _(statistical approximation)_")
  }

  def helpMessage(hint: String, command: String) = {
    Ok(slackResponse(s"_Help_: `$command [roll] [a times|x] [x]dy [+z] [reroll i|i to j] [drop highest|lowest [c]]`\ne.g. `roll 4 times 4d6 drop lowest`, `d20 + 15`, `7d10+10 reroll 1 to 2`", hint))
  }

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
