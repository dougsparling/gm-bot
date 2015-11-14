package ca.dougsparling.gmbot

import org.scalatra._
import scalate.ScalateSupport

class GmBotServlet extends GmBotStack {

  get("/") {
    <html>
      <body>
        <h1>Hello, world!</h1>
        Say <a href="hello-scalate">hello to Scalate</a>.
      </body>
    </html>
  }

}
