package ca.dougsparling.gmbot

import org.scalatra.test.specs2._

// For more on Specs2, see http://etorreborre.github.com/specs2/guide/org.specs2.guide.QuickStart.html
class GmBotServletSpec extends ScalatraSpec { def is =
  "POST /roll on GmBotServlet"                     ^
    "should return status 200"                     ! postRoll200^
    "should have content-type application/json"    ! postRollJson^
                                                end

  addServlet(classOf[GmBotServlet], "/*")

  def postRoll200 = post("/roll", "text" -> "5d6") {
    status must_== 200
  }

  def postRollJson = post("/roll", "text" -> "5d6") {
    header must havePair("Content-Type" -> "application/json")
  }
}
