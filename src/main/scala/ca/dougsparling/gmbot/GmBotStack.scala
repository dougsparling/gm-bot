package ca.dougsparling.gmbot

import org.scalatra._
import jakarta.servlet.http.HttpServletRequest

trait GmBotStack extends ScalatraServlet {

  notFound {
    contentType = null
    serveStaticResource() getOrElse resourceNotFound()
  }

}
