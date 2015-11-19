package ca.dougsparling.gmbot

import ca.dougsparling.gmbot.parser.{RollSpec, RollSpecParser}
import ca.dougsparling.gmbot.roller._
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.Ok
import org.scalatra.json.JacksonJsonSupport

class GmBotServlet extends GmBotStack with JacksonJsonSupport {

  protected implicit lazy val jsonFormats: Formats = DefaultFormats
  protected lazy val secureRoller = new RollSpecRunner with SecureDice

  post("/roll") {
    val req = parseRequest()
    val parseResult = RollSpecParser.parseAll(RollSpecParser.roll, req.text)
    parseResult match {
      case RollSpecParser.NoSuccess(msg, next) => helpMessage(msg, req.command)
      case RollSpecParser.Success(spec, _) if spec.die >= 10000 => Ok(slackResponse(s"Sorry, I can't find a ${spec.die} sided die."))
      case RollSpecParser.Success(spec, _) if spec.number > 100 => Ok(slackResponse(s"I'm not going to roll ${spec.number} dice, that'll take forever."))
      case RollSpecParser.Success(spec, _) if spec.repeat > 20 => Ok(slackResponse(s"I have to put my foot down on stupid requests. Like that one."))
      case RollSpecParser.Success(spec, _) => {
        val result = secureRoller.run(spec)
        result match {
          case Left(rolls) => Ok(renderResult(req, spec, rolls))
          case Right(err) => Ok(slackResponse(err))
        }
      }
    }
  }

  def parseRequest() = {
    //val reqMap = body.split("\n").map(_.split("=")).map(pair => (pair(0), pair(1))).toMap
    SlackRequest(params("text"), params("command"), params("user_name"))
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

  def helpMessage(hint: String, command: String) = {
    Ok(slackResponse(s"_Help_: `$command [a times|x] [x]dy [+z] [reroll i|i to j] [drop highest|lowest [c]]`\ne.g. `4 times 4d6 drop lowest`, `d20 + 15`, `7d10+10 reroll 1 to 2`", hint))
  }

  def slackResponse(text: String, attachments: String*) = {
    contentType = formats("json")
    SlackResponse(text, attachments.map(Attachment))
  }


}
case class SlackRequest(text: String, command: String, who: String)
case class Attachment(text: String)
case class SlackResponse(text: String, attachments: Seq[Attachment], response_type: String = "in_channel")
