package ca.dougsparling.gmbot

import org.scalatra._
import jakarta.servlet.http.HttpServletRequest
import collection.mutable

trait GmBotStack extends ScalatraServlet {

  notFound {
    // remove content type in case it was set through an action
    contentType = null
    
    serveStaticResource() getOrElse resourceNotFound()
  }

}
