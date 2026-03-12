package ca.dougsparling.gmbot

import com.slack.api.app_backend.SlackSignature
import jakarta.servlet._
import jakarta.servlet.http._
import java.io.ByteArrayInputStream
import org.slf4j.LoggerFactory

/** Reads and caches the raw request body, then verifies the Slack request
 *  signature before passing to the servlet. The body must be cached because
 *  the servlet container consumes the input stream during form parameter parsing.
 */
class SlackSignatureFilter extends Filter {

  private val logger = LoggerFactory.getLogger(getClass)

  private val verifier: Option[SlackSignature.Verifier] =
    System.getenv("SLACK_SIGNING_SECRET") match {
      case null =>
        sys.error("SLACK_SIGNING_SECRET is not set. Use your Slack signing secret, or 'disabled' to skip verification.")
      case "disabled" =>
        logger.warn("SLACK_SIGNING_SECRET=disabled — request signature verification is OFF")
        None
      case secret =>
        Some(new SlackSignature.Verifier(new SlackSignature.Generator(secret)))
    }

  def doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain): Unit =
    (req, res) match {
      case (req: HttpServletRequest, res: HttpServletResponse) =>
        val body      = req.getInputStream.readAllBytes()
        val timestamp = req.getHeader("X-Slack-Request-Timestamp")
        val signature = req.getHeader("X-Slack-Signature")

        val verified = verifier.forall { v =>
          val bodyStr = new String(body, req.getCharacterEncoding match { case null => "UTF-8" case enc => enc })
          val ok = timestamp != null && signature != null && v.isValid(timestamp, bodyStr, signature)
          if (!ok) logger.warn(s"Signature verification failed — timestamp=$timestamp, bodyLen=${body.length}")
          ok
        }

        if (!verified) {
          res.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid request signature")
        } else {
          chain.doFilter(new HttpServletRequestWrapper(req) {
            override def getInputStream = new ServletInputStream {
              private val in = new ByteArrayInputStream(body)
              def read() = in.read()
              def isFinished = in.available() == 0
              def isReady = true
              def setReadListener(l: ReadListener) = ()
            }
            override def getReader =
              new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream, req.getCharacterEncoding))
          }, res)
        }

      case _ => chain.doFilter(req, res)
    }
}
