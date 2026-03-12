package ca.dougsparling.gmbot

import com.slack.api.app_backend.SlackSignature
import org.scalatra._
import org.slf4j.LoggerFactory
import jakarta.servlet.http.HttpServletRequest

trait GmBotStack extends ScalatraServlet {

  private val logger = LoggerFactory.getLogger(getClass)

  private val verifier: Option[SlackSignature.Verifier] =
    System.getenv("SLACK_SIGNING_SECRET") match {
      case null =>
        sys.error("SLACK_SIGNING_SECRET environment variable is not set. Set it to your Slack signing secret, or 'disabled' to skip verification.")
      case "disabled" =>
        logger.warn("SLACK_SIGNING_SECRET=disabled — request signature verification is OFF")
        None
      case secret =>
        Some(new SlackSignature.Verifier(new SlackSignature.Generator(secret)))
    }

  before("/roll") {
    verifier.foreach { v =>
      val timestamp = request.getHeader("X-Slack-Request-Timestamp")
      val signature = request.getHeader("X-Slack-Signature")
      val body      = request.body
      if (timestamp == null || signature == null || !v.isValid(timestamp, body, signature)) {
        halt(403, "Invalid request signature")
      }
    }
  }

  notFound {
    contentType = null
    serveStaticResource() getOrElse resourceNotFound()
  }

}
