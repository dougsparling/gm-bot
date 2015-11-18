package ca.dougsparling.gmbot

import ca.dougsparling.gmbot.parser.RollSpecParser
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.Ok
import org.scalatra.json.NativeJsonSupport

class GmBotServlet extends GmBotStack with NativeJsonSupport {

  protected implicit lazy val jsonFormats: Formats = DefaultFormats

  post("/roll") {
    val rollStr = params("text")
    val parseResult = RollSpecParser.parseAll(RollSpecParser.roll, rollStr)
    parseResult match {
      case RollSpecParser.NoSuccess(msg, next) => helpMessage(msg)
      case RollSpecParser.Success(_, _) => Ok(slackResponse("huh"))
    }
  }

  def helpMessage(hint: String) = {
    Ok(slackResponse("Help: /roll 5d6", hint))
  }

  def slackResponse(text: String, attachments: String*) = SlackResponse(text, attachments.map(Attachment))

  case class Attachment(text: String)
  case class SlackResponse(text: String, attachments: Seq[Attachment])
}
